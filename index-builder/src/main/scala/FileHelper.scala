import java.nio.file.Files
import java.nio.file.Path

import cats.NonEmptyParallel
import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import cats.implicits._

object Helper {
  private def existsCheck[F[_]: Sync](path: Path): F[Boolean] = Sync[F].delay(Files.exists(path))
  private def directoryCheck[F[_]: Sync](path: Path): F[Boolean] = Sync[F].delay(Files.isDirectory(path))
  private def writableCheck[F[_]: Sync](path: Path): F[Boolean] = Sync[F].delay(Files.isWritable(path))

  def checkPath[F[_]: NonEmptyParallel: Sync: ContextShift](path: Path, blocker: Blocker): F[Boolean] = {
    val allChecks: F[Boolean] = (existsCheck(path), directoryCheck(path), writableCheck(path)).parMapN(_ && _ && _)
    blocker.blockOn(allChecks)
  }
}
