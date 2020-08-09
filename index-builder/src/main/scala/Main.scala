import java.nio.file.Path

import b2.B2Client
import b2.B2Credentials
import b2.models.TokenResponse
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import db.Connection
import db.Migrations
import db.Queries
import doobie._
import doobie.implicits._
import services.FileService
import site.Hugo

object CommandOptions {
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
  val hugoTargetOpts =
    Opts.option[Path]("hugo-target", help = "The directory where the hugo template is located.")
}

object MainCommands {
  import CommandOptions._

  sealed trait CLICommand extends Product with Serializable
  final case class BuildIndexCommand(b2BucketName: String, b2AppKeyId: String, b2AppKey: String, workDir: Path) extends CLICommand
  final case class BuildHugoSiteCommand(b2AppKeyId: String, b2AppKey: String, workDir: Path, hugoTarget: Path) extends CLICommand

  private val buildIndexSubCommand =
    Opts.subcommand(
      Command(
        name = "build-index",
        header = "Look up files in the B2 bucket and build a local index."
      ) {
        (b2BucketNameOpts, b2AppKeyIdOpts, b2AppKeyOpts, workDirOpts).mapN { BuildIndexCommand }
      }
    )

  private val buildSiteSubCommand = Opts.subcommand(
    Command(
      name = "build-hugo-site",
      header = "Read the index, download files and generate hugo template files."
    ) {
      (b2AppKeyIdOpts, b2AppKeyOpts, workDirOpts, hugoTargetOpts).mapN { BuildHugoSiteCommand }
    }
  )

  val allCommands: Opts[CLICommand] = buildIndexSubCommand orElse buildSiteSubCommand
}

object RunBuildIndexCommand {
  def apply(cmd: MainCommands.BuildIndexCommand)(implicit cs: ContextShift[IO], clock: Clock[IO], timer: Timer[IO]): IO[Unit] = {
    def program(blocker: Blocker): IO[Unit] = {
      val workDir = cmd.workDir
      val credentials = B2Credentials(cmd.b2AppKeyId, cmd.b2AppKey)

      def clientProgram(fileService: FileService[IO]) =
        for {
          token <- B2Client.getToken[IO](credentials)
          buckets <- B2Client.listBuckets[IO](token)

          selectedBucket <- B2Client.Client.liftF(
            buckets.buckets
              .find(_.bucketName === cmd.b2BucketName)
              .liftTo[IO](new IllegalArgumentException(s"Bucket ${cmd.b2BucketName} was not found in ${buckets.buckets.mkString("[", ",", "]")}"))
          )

          _ <-
            B2Client
              .listFileNames[IO](selectedBucket.bucketId, token)
              .mapF(
                _.flatMap(filesStream => fileService.buildIndex(filesStream))
              )
        } yield ()

      val transactor = Connection.acquireConnection[IO](workDir, blocker)
      val fileService = new FileService[IO](transactor, blocker)
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
      } yield ()
    }

    Blocker[IO].use(program(_))
  }
}

object RunBuildHugoSiteCommand {
  def apply(cmd: MainCommands.BuildHugoSiteCommand)(implicit cs: ContextShift[IO], clock: Clock[IO], timer: Timer[IO]): IO[Unit] = {
    def program(blocker: Blocker): IO[Unit] = {
      val workDir = cmd.workDir
      val hugoTarget = cmd.hugoTarget
      val credentials = B2Credentials(cmd.b2AppKeyId, cmd.b2AppKey)

      def clientProgram(fileService: FileService[IO], hugo: Hugo) = {
        def downloadIfNotExist(token: TokenResponse)(file: models.File, target: java.io.File): B2Client.Client[IO, Unit] = {
          B2Client.Client.liftF(fileService.computeSha1(target)).flatMap { fileSha1 =>
            if (fileSha1.exists(_ === file.contentSha1)) {
              ().pure[B2Client.Client[IO, ?]]
            } else {
              B2Client.downloadFile(token, file.fileId, target)
            }
          }
        }
        for {
          token <- B2Client.getToken[IO](credentials)
          filesStream = Queries.Files.stream().take(100)
          _ <- fileService.buildSite(workDir, hugoTarget, filesStream, downloadIfNotExist(token), hugo.buildPage[IO], LiftIO.liftK[ConnectionIO])
        } yield ()
      }

      val transactor = Connection.acquireConnection[IO](workDir, blocker)
      val fileService = new FileService[IO](transactor, blocker)
      val hugo = new Hugo(blocker)
      val migrationsC = Migrations.runMigrations()

      def isValidPath(path: Path): IO[Unit] = {
        Helper
          .checkPath[IO](path, blocker)
          .ensure(new IllegalArgumentException(s"The directory is invalid: $path"))(identity)
          .as(())
      }

      for {
        _ <- isValidPath(workDir)
        _ <- isValidPath(hugoTarget)

        _ <- migrationsC.transact(transactor)

        _ <- B2Client.run[IO, Unit](clientProgram(fileService, hugo))
      } yield ()
    }

    Blocker[IO].use(program(_))
  }
}

object Main
    extends CommandIOApp(
      name = "pstor-cli",
      header = "CLI to work with a pstor bucket."
    ) {
  override def main: Opts[IO[ExitCode]] = {
    import MainCommands._

    allCommands map {
      case cmd: BuildHugoSiteCommand => RunBuildHugoSiteCommand(cmd).as(ExitCode.Success)
      case cmd: BuildIndexCommand    => RunBuildIndexCommand(cmd).as(ExitCode.Success)
    }
  }
}
