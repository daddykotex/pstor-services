package services

import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant

import scala.concurrent.duration._

import b2.B2Client
import b2.models.{File => B2File}
import cats._
import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import db.Queries
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import models.File
import models.FileStatus
import org.apache.commons.codec.digest.DigestUtils
import site.Hugo
import java.nio.file.StandardCopyOption
import java.io.FileNotFoundException

class FileService[F[_]: ApplicativeError[*[_], Throwable]: Bracket[*[_], Throwable]: Sync: ContextShift: Clock: Concurrent: Timer: NonEmptyParallel](
    transactor: Transactor[F],
    blocker: Blocker
) {
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

  def computeSha1(file: java.io.File): F[Option[String]] = {
    val acquireIS = blocker.delay[F, InputStream](new FileInputStream(file))
    val releaseIS: InputStream => F[Unit] = is => blocker.delay[F, Unit](is.close())
    // fs2.io.file.readAll[F](file.toPath(), blocker, 1024).through(fs2.hash.sha1)
    Resource
      .make[F, InputStream](acquireIS)(releaseIS)
      .use { is =>
        DigestUtils.sha1Hex(is).some.pure[F]
      }
      .handleError {
        case _: FileNotFoundException => None
      }
  }

  def buildSite(
      workdir: Path,
      hugoTarget: Path,
      files: fs2.Stream[ConnectionIO, File],
      downloadOp: (File, java.io.File) => B2Client.Client[F, Unit],
      siteOp: (File, Instant, java.io.File) => F[Unit],
      connectionIOLift: FunctionK[F, ConnectionIO]
  ): B2Client.Client[F, Unit] = {
    def markWithStatus(file: File, status: FileStatus): ConnectionIO[File] = {
      val updated = file.copy(status = status)
      Queries.Files.updateFile(updated).as(updated)
    }

    val workdirImagesFolderPath = workdir.resolve(Hugo.ContentPaths.photoFileFolder)
    val hugoImagesFolderPath = hugoTarget.resolve(Hugo.ContentPaths.photoFileFolder)
    def photoFileTarget(target: Path, file: File): Path = {
      target.resolve(file.fileName)
    }

    val workdirContentFolderPath = workdir.resolve(Hugo.ContentPaths.photoContentFolder)
    val hugoContentFolderPath = hugoTarget.resolve(Hugo.ContentPaths.photoContentFolder)
    def photoContentTarget(target: Path, file: File): Path = {
      val fileName = s"${File.stripExtension(file.fileName)}.md"
      target.resolve(fileName)
    }

    def downloadFile(file: File, client: B2Client.Backend[F]): F[java.io.File] = {
      val target = photoFileTarget(workdirImagesFolderPath, file).toFile()
      downloadOp(file, target).as(target).run(client)
    }

    def writeContentFile(file: File): F[java.io.File] = {
      val target = photoContentTarget(workdirContentFolderPath, file).toFile()
      Clock[F]
        .realTime(MILLISECONDS)
        .map(Instant.ofEpochMilli)
        .flatMap { currentTime =>
          siteOp(file, currentTime, target)
        }
        .as(target)
    }

    def prepare(): F[Unit] = {
      val folders: List[java.io.File] = List(
        hugoContentFolderPath.toFile(),
        hugoImagesFolderPath.toFile(),
        workdirContentFolderPath.toFile(),
        workdirImagesFolderPath.toFile()
      )
      val createFolders: List[F[Unit]] = folders.map { file =>
        blocker
          .delay[F, Boolean](file.mkdirs())
          .flatMap {
            case true => ().pure[F]
            case false =>
              if (!file.exists()) {
                ApplicativeError[F, Throwable].raiseError(new IllegalStateException(s"Cannot create directories: [${file.getAbsolutePath()}]"))
              } else {
                ().pure[F]
              }
          }
      }
      createFolders.sequence.as(())
    }

    final case class AssemblyQueue(queue: fs2.concurrent.Queue[F, Option[(File, Hugo.Artifact)]], complete: F[Unit])
    def buildAssemblyQueue(hugoTarget: Path): F[AssemblyQueue] = {

      def copyFileToPath(file: java.io.File, relativeTarget: Path): F[Unit] = {
        val target = hugoTarget.resolve(relativeTarget)
        blocker
          .delay[F, Path](java.nio.file.Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING))
          .as(())
      }
      Sync[F].delay(println(s"Assembling Hugo files into $hugoTarget")) *>
        fs2.concurrent.Queue.unbounded[F, Option[(File, Hugo.Artifact)]].flatMap { queue =>
          val draining = Concurrent[F]
            .start(
              queue.dequeue.unNoneTerminate
                .evalTap {
                  case (file, Hugo.Artifact(photoFile, photoContent)) =>
                    val cpFile: F[Unit] = copyFileToPath(photoFile, photoFileTarget(hugoImagesFolderPath, file))
                    val cpContent: F[Unit] = copyFileToPath(photoContent, photoContentTarget(hugoContentFolderPath, file))
                    (cpContent, cpFile).parMapN { (_, _) => () }
                }
                .compile
                .drain
            )
          draining.map(fiber => AssemblyQueue(queue, queue.offer1(None).flatMap(_ => fiber.join)))
        }
    }

    def runFile(file: File, client: B2Client.Backend[F]): ConnectionIO[(File, Hugo.Artifact)] = {
      for {
        downloading <- markWithStatus(file, FileStatus.Downloading)
        resultPhotoFile <- connectionIOLift(downloadFile(downloading, client))
        startSiteProcess <- markWithStatus(file, FileStatus.SiteProcessing)
        resultContentFile <- connectionIOLift(writeContentFile(startSiteProcess))
        doneSiteProcess <- markWithStatus(file, FileStatus.SiteProcessed)
        res = Hugo.Artifact(resultPhotoFile, resultContentFile)
      } yield doneSiteProcess -> res
    }

    def run(assemblyQueue: AssemblyQueue): B2Client.Client[F, Unit] =
      B2Client.Client { client =>
        val runFiles = files
          .filter(_.contentType === "image/jpeg")
          .evalMap(file => runFile(file, client))
          .through(_.evalMap { pair => connectionIOLift(assemblyQueue.queue.offer1(Some(pair))) })
          .compile
          .drain
          .transact(transactor)
        runFiles *> assemblyQueue.complete
      }

    B2Client.Client.liftF(prepare()) *>
      B2Client.Client.liftF(buildAssemblyQueue(hugoTarget)) flatMap { assembly => run(assembly) }
  }
}
