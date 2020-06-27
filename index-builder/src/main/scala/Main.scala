import cats.implicits._

import com.monovore.decline._
import com.monovore.decline.effect._
import cats.effect.{ExitCode, IO}
import b2.B2Credentials
import b2.B2Client
import b2.models.BucketResponse
import java.nio.file.Path
import cats.effect.Blocker

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
          val clientProgram = for {
            token <- B2Client.getToken[IO](credentials)
            bucketStr <- B2Client.listBuckets[IO](token)
          } yield bucketStr

          for {
            workDirCheck <- Helper.checkPath[IO](workDir, blocker)
            bucketResponse <- B2Client.run[IO, BucketResponse](clientProgram)
            _ <- IO.delay {
              bucketResponse.buckets.foreach { b =>
                println(s"Bucket name: ${b.bucketName}, bucket ID: ${b.bucketId}")
              }
              if (workDirCheck) {
                println(s"Proceeding with ${workDir.toAbsolutePath().toString()}")
              } else {
                println(s"Can't proceed with ${workDir.toAbsolutePath().toString()}")
              }
            }
          } yield ExitCode.Success
        }

        Blocker[IO].use(program(_))
    }
  }
}
