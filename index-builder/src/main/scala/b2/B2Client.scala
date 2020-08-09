package b2

import b2.models.ListFileNameRequest
import b2.models._
import cats.MonadError
import cats.data.Kleisli
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.implicits._
import cats.kernel.Eq
import fs2.Stream
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client.circe._
import sttp.model.StatusCode

object B2Client {
  type Backend[F[_]] = SttpBackend[F, Stream[F, Byte], WebSocketHandler]
  type Client[F[_], T] = Kleisli[F, Backend[F], T]
  type FError[F[_]] = MonadError[F, Throwable]

  object Client {
    def apply[F[_], T](run: Backend[F] => F[T]): Client[F, T] =
      Kleisli[F, Backend[F], T] { b => run(b) }
    def liftF[F[_], T](f: F[T]): Client[F, T] = Kleisli.liftF(f)
  }

  private implicit val statusCodeEq: Eq[StatusCode] = Eq.instance { (sc1, sc2) => sc1.code === sc2.code }

  private val b2ApiVersion = "v2"
  private object Actions {
    val downloadFileById = "b2_download_file_by_id"
    val listFileNames = "b2_list_file_names"
    val listBuckets = "b2_list_buckets"
    val authorizeAccount = "b2_authorize_account"
  }

  private val tokenUri =
    uri"https://api.backblazeb2.com/b2api/$b2ApiVersion/${Actions.authorizeAccount}"

  private def buildUri(action: String, apiUrl: String): sttp.model.Uri =
    uri"$apiUrl/b2api/$b2ApiVersion/$action"

  def getToken[F[_]](
      credentials: B2Credentials
  )(implicit ME: FError[F]): Client[F, TokenResponse] = {
    Client { implicit backend =>
      basicRequest
        .get(tokenUri)
        .auth
        .basic(credentials.keyId, credentials.key)
        .response(asJson[TokenResponse])
        .send()
        .flatMap { raiseOnFailure(_)(ME) }
    }
  }

  def listBuckets[F[_]](
      token: TokenResponse
  )(implicit ME: FError[F]): Client[F, BucketResponse] = {
    val uri = buildUri(Actions.listBuckets, token.apiUrl).param("accountId", token.accountId)
    Client { implicit backend =>
      basicRequest
        .get(uri)
        .header("Authorization", token.authorizationToken)
        .response(asJson[BucketResponse])
        .send()
        .flatMap { raiseOnFailure(_)(ME) }
    }
  }

  def listFileNames[F[_]](bucketId: String, token: TokenResponse)(implicit ME: FError[F]): Client[F, Stream[F, File]] = {
    def go(body: ListFileNameRequest): Client[F, ListFileResponse] =
      Client { implicit backend =>
        val uri = buildUri(Actions.listFileNames, token.apiUrl)
        basicRequest
          .post(uri)
          .body(body)
          .header("Authorization", token.authorizationToken)
          .response(asJson[ListFileResponse])
          .send()
          .flatMap { raiseOnFailure(_)(ME) }
      }

    Client { backend =>
      fs2.Stream
        .unfoldLoopEval[F, ListFileNameRequest, List[File]](ListFileNameRequest(bucketId, None)) { body =>
          go(body)
            .run(backend)
            .map {
              case ListFileResponse(files, nextFileName) =>
                val maybeNext = nextFileName.map(fileName => ListFileNameRequest(bucketId, Some(fileName)))
                (files, maybeNext)
            }
        }
        .flatMap(fs2.Stream.emits)
        .pure[F]
    }
  }

  def downloadFile[F[_]](
      token: TokenResponse,
      fileId: String,
      target: java.io.File
  )(implicit ME: FError[F]): Client[F, Unit] = {
    val uri = buildUri(Actions.downloadFileById, token.downloadUrl).param("fileId", fileId)
    Client { implicit backend =>
      basicRequest
        .get(uri)
        .header("Authorization", token.authorizationToken)
        .response(asFile(target))
        .send()
        .flatMap { r =>
          if (r.code === StatusCode.Ok) {
            ().pure[F]
          } else {
            ME.raiseError(
              new RuntimeException(
                s"Request failed [${r.code.code}]. Result in ${target.getAbsolutePath}"
              )
            )
          }
        }
    }
  }

  def run[F[_]: ConcurrentEffect: ContextShift, T](
      client: Client[F, T]
  ): F[T] = {
    AsyncHttpClientFs2Backend.resource().use { implicit backend =>
      client.run(backend)
    }
  }

  private def raiseOnFailure[F[_], T, R](r: Response[Either[T, R]])(implicit ME: FError[F]): F[R] = {
    r.body.fold(
      err =>
        ME.raiseError(
          new RuntimeException(
            s"Request failed [${r.code.code}] with body $err"
          )
        ),
      body => ME.pure(body)
    )
  }
}
