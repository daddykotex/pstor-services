import cats.implicits._
import com.monovore.decline._

object Main extends CommandApp(
  name = "index-builder",
  header = "Build an index from a B2 bucket.",
  main = {
    val bucketName =
      Opts.option[String]("bucket-name", help = "The bucket name. The credentials you will use need to be able to download files from this bucket")

    val b2AppKeyId =
      Opts.option[String]("key-id", help = "A B2 Cloud Key ID")
    val b2AppKey =
      Opts.option[String]("key", help = "A B2 Cloud Key")

    (bucketName, b2AppKeyId, b2AppKey).mapN { (b2Name, keyId, key) =>
      println(s"$b2Name $keyId $key")
    }
  }
)