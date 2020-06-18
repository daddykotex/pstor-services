package b2

import cats.data.Kleisli
import cats.implicits._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client._
import sttp.client.circe._
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import fs2.Stream
import cats.MonadError
import b2.models.TokenResponse

object B2Client {
  type Backend[F[_]] = SttpBackend[F, Stream[F, Byte], WebSocketHandler]
  type Client[F[_], T] = Kleisli[F, Backend[F], T]
  type FError[F[_]] = MonadError[F, Throwable]

  object Client {
    def apply[F[_], T](run: Backend[F] => F[T]): Kleisli[F, Backend[F], T] =
      Kleisli[F, Backend[F], T] { b => run(b) }
  }

  private val tokenUri =
    uri"https://api.backblazeb2.com/b2api/v2/b2_authorize_account"

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
        .flatMap { r =>
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

  }

  def run[F[_]: ConcurrentEffect: ContextShift, T](
      client: Client[F, T]
  ): F[T] = {
    AsyncHttpClientFs2Backend.resource().use { implicit backend =>
      client.run(backend)
    }
  }
}
