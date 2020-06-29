package db

import java.nio.file.Path

import cats.effect._
import doobie._

object Connection {
  private val DbFileName = "pstor.db"

  def acquireConnection[F[_]: Async: ContextShift](file: Path, blocker: Blocker): Transactor[F] = {
    val fullDbFile = file.resolve(DbFileName)
    Transactor.fromDriverManager[F](
      "org.sqlite.JDBC", // driver classname
      s"jdbc:sqlite:${fullDbFile.toAbsolutePath().toString()}",
      blocker
    )
  }
}
