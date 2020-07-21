package services

import b2.models.{File => B2File}
import models.File
import cats.Applicative
import cats.effect._
import cats.implicits._
import db.Queries
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

class FileService[F[_]: Applicative: Bracket[*[_], Throwable]: Sync](transactor: Transactor[F]) {
  def buildIndex(files: fs2.Stream[F, B2File]): F[Unit] = {
    files
      .map { file =>
        for {
          found <- Queries.Files.findByName(file.fileName)
          _ <- found match {
            case None =>
              Queries.Files.insert(File.pending(file))
            case Some(_) =>
              0.pure[ConnectionIO]
          }
        } yield ()
      }
      .evalMap(_.transact(transactor))
      .compile
      .drain
  }
}
