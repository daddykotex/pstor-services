import java.nio.file.Path

import b2.B2Client
import b2.B2Credentials
import cats.effect.Blocker
import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import db.Connection
import db.Migrations
import doobie.implicits._
import services.FileService

object Main
    extends CommandIOApp(
      name = "index-builder",
      header = "Build an index from a B2 bucket."
    ) {
  override def main: Opts[IO[ExitCode]] = {
    val b2BucketNameOpts =
      Opts.option[String](
        "bucket-name",
        help = "The bucket name. The credentials you will use need to be able to download files from this bucket"
      )

    val b2AppKeyIdOpts =
      Opts.option[String]("key-id", help = "A B2 Cloud Key ID")
    val b2AppKeyOpts =
      Opts.option[String]("key", help = "A B2 Cloud Key")

    val workDirOpts =
      Opts.option[Path]("work-dir", help = "The directory where the index will be built.")

    (b2BucketNameOpts, b2AppKeyIdOpts, b2AppKeyOpts, workDirOpts).mapN {
      case (_, keyId, key, workDir) =>
        def program(blocker: Blocker): IO[ExitCode] = {
          val credentials = B2Credentials(keyId, key)
          def clientProgram(fileService: FileService[IO]) =
            for {
              token <- B2Client.getToken[IO](credentials)
              buckets <- B2Client.listBuckets[IO](token)

              _ <- buckets.buckets.map { bucket =>
                B2Client
                  .listFileNames[IO](bucket.bucketId, token)
                  .mapF(
                    _.flatMap(filesStream => fileService.buildIndex(filesStream))
                  )
              }.sequence
            } yield ()

          val transactor = Connection.acquireConnection[IO](workDir, blocker)
          val fileService = new FileService[IO](transactor)
          val migrationsC = Migrations.runMigrations()

          for {
            workDirCheck <- Helper.checkPath[IO](workDir, blocker)
            _ <- migrationsC.transact(transactor)

            _ <- IO.delay {
              if (workDirCheck) {
                println(s"Proceeding with ${workDir.toAbsolutePath().toString()}")
              } else {
                println(s"Can't proceed with ${workDir.toAbsolutePath().toString()}")
              }
            }
            _ <- B2Client.run[IO, Unit](clientProgram(fileService))
          } yield ExitCode.Success
        }

        Blocker[IO].use(program(_))
    }
  }
}
