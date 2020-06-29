package db

import scala.annotation.tailrec

import b2.models.File
import cats.implicits._
import doobie._
import doobie.implicits._

object Queries {

  object Files {
    val count: ConnectionIO[Long] =
      sql"SELECT COUNT(*) FROM files".query[Long].unique

    def findByName(fileName: String): ConnectionIO[Option[File]] =
      sql"SELECT file_id, file_name, content_length, content_sha1 FROM files WHERE file_name = $fileName".query[File].option

    def insert(file: File): ConnectionIO[Int] =
      sql"""
        INSERT INTO `files`(file_id, file_name, content_length, content_sha1)
        VALUES(${file.fileId}, ${file.fileName}, ${file.contentLength}, ${file.contentSha1})
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
      CREATE TABLE IF NOT EXISTS files(
        id int PRIMARY KEY,
        file_id VARCHAR(512) NOT NULL UNIQUE,
        file_name VARCHAR(512) NOT NULL,
        content_length INT NOT NULL,
        content_sha1 VARCHAR(512) NOT NULL
      );
    """.update

  private val migrations: Map[Int, Update0] = Map(
    0 -> initialMigration
  )
}
