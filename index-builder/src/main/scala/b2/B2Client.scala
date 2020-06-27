package b2

import b2.models.BucketResponse
import b2.models.TokenResponse
import cats.MonadError
import cats.data.Kleisli
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.implicits._
import fs2.Stream
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client.circe._

object B2Client {
  type Backend[F[_]] = SttpBackend[F, Stream[F, Byte], WebSocketHandler]
  type Client[F[_], T] = Kleisli[F, Backend[F], T]
  type FError[F[_]] = MonadError[F, Throwable]

  object Client {
    def apply[F[_], T](run: Backend[F] => F[T]): Kleisli[F, Backend[F], T] =
      Kleisli[F, Backend[F], T] { b => run(b) }
  }

  private val b2ApiVersion = "v2"

  private val tokenUri =
    uri"https://api.backblazeb2.com/b2api/$b2ApiVersion/b2_authorize_account"

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
    val uri = buildUri("b2_list_buckets", token.apiUrl).param("accountId", token.accountId)
    Client { implicit backend =>
      basicRequest
        .get(uri)
        .header("Authorization", token.authorizationToken)
        .response(asJson[BucketResponse])
        .send()
        .flatMap { raiseOnFailure(_)(ME) }
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
