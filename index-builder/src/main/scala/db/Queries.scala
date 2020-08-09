package db

import scala.annotation.tailrec

import cats.arrow._
import cats.effect.implicits._
import cats.implicits._
import doobie._
import doobie.implicits._
import models.File

object Queries {

  def withoutTransaction[A](p: ConnectionIO[A]): ConnectionIO[A] =
    FC.setAutoCommit(true).bracket(_ => p)(_ => FC.setAutoCommit(false))

  val withoutTransactionK: FunctionK[ConnectionIO, ConnectionIO] = new FunctionK[ConnectionIO, ConnectionIO] {
    def apply[A](p: ConnectionIO[A]): ConnectionIO[A] = withoutTransaction(p)
  }

  object Files {
    val count: ConnectionIO[Long] =
      sql"SELECT COUNT(id) FROM files".query[Long].unique

    def stream(): fs2.Stream[ConnectionIO, File] =
      sql"SELECT `file_id`, `file_name`, `content_length`, `content_type`, `content_sha1`, `status` FROM `files`"
        .query[File]
        .stream
        .translate(withoutTransactionK)

    def findByName(fileName: String): ConnectionIO[Option[File]] =
      sql"SELECT `file_id`, `file_name`, `content_length`, `content_type`, `content_sha1`, `status` FROM `files` WHERE `file_name` = $fileName"
        .query[File]
        .option

    def insert(file: File): ConnectionIO[Int] =
      sql"""
        INSERT INTO `files`(`file_id`, `file_name`, `content_length`, `content_type`, `content_sha1`, `status`)
        VALUES(${file.fileId}, ${file.fileName}, ${file.contentLength}, ${file.contentType}, ${file.contentSha1}, ${file.status})
        """.update.run

    def updateFile(file: File): ConnectionIO[Int] =
      sql"""
        UPDATE `files` SET `file_name` = ${file.fileName}, `content_length` = ${file.contentLength}, `content_type` = ${file.contentType}, `content_sha1` = ${file.contentSha1}, `status` = ${file.status}
        WHERE `file_id` = ${file.fileId}
        """.update.run

  }
}

object Migrations {
  private val schemaVersion: ConnectionIO[Int] =
    sql"PRAGMA user_version;".query[Int].unique

  private def updateSchemaVersion(version: Int): Update0 =
    Fragment.const(s"PRAGMA user_version = $version;").update

  def runMigrations(): ConnectionIO[Unit] = {
    @tailrec
    def go(version: Int, next: ConnectionIO[Unit]): ConnectionIO[Unit] = {
      migrations
        .get(version) match {
        case Some(value) =>
          val run = for {
            _ <- next
            _ <- value.run
            _ <- updateSchemaVersion(version).run
          } yield ()

          go(version + 1, run)
        case None =>
          next
      }
    }

    for {
      sv <- schemaVersion
      _ <- go(sv, ().pure[ConnectionIO])
    } yield ()
  }

  private val initialMigration: Update0 = sql"""
      CREATE TABLE IF NOT EXISTS `files`(
        `file_id` VARCHAR(512) NOT NULL UNIQUE,
        `file_name` VARCHAR(512) NOT NULL,
        `content_length` INT NOT NULL,
        `content_type` VARCHAR(512) NOT NULL,
        `content_sha1` VARCHAR(512) NOT NULL,
        `status` VARCHAR(128) NOT NULL
      );
    """.update

  private val migrations: Map[Int, Update0] = Map(
    0 -> initialMigration
  )
}
