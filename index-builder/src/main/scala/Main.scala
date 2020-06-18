import cats.implicits._

import com.monovore.decline._
import com.monovore.decline.effect._
import cats.effect.{ExitCode, IO}
import b2.B2Credentials
import b2.B2Client
import b2.models.BucketResponse

object Main
    extends CommandIOApp(
      name = "index-builder",
      header = "Build an index from a B2 bucket."
    ) {
  override def main: Opts[IO[ExitCode]] = {
    val bucketName =
      Opts.option[String](
        "bucket-name",
        help = "The bucket name. The credentials you will use need to be able to download files from this bucket"
      )

    val b2AppKeyId =
      Opts.option[String]("key-id", help = "A B2 Cloud Key ID")
    val b2AppKey =
      Opts.option[String]("key", help = "A B2 Cloud Key")

    (bucketName, b2AppKeyId, b2AppKey).mapN {
      case (_, keyId, key) =>
        val credentials = B2Credentials(keyId, key)
        val clientProgram = for {
          token <- B2Client.getToken[IO](credentials)
          bucketStr <- B2Client.listBuckets[IO](token)
        } yield bucketStr

        val tokenIO =
          B2Client.run[IO, BucketResponse](clientProgram)
        tokenIO
          .flatTap(bucketResponse =>
            IO.delay {
              bucketResponse.buckets.foreach { b =>
                print(s"Bucket name: ${b.bucketName}, bucket ID: ${b.bucketId}")
              }
            }
          )
          .as(ExitCode.Success)
    }
  }
}
