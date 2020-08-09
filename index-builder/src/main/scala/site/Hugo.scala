package site

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

import cats.Applicative
import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Sync
import models.File
import java.nio.file.StandardOpenOption

object Hugo {
  final case class Artifact(photoFile: java.io.File, photoContent: java.io.File)
  object ContentPaths {
    val photoFileFolder: Path = Paths.get("static/images")
    val photoContentFolder: Path = Paths.get("content/photos")
  }
  object ContentTemplates {

    /**
      * The path is relative to the root (/) of the site and must be a path
      * that exists in the /static directory of the Hugo site.
      */
    val photo = s"""|---
                    |title: "<<title>>"
                    |date: "<<date>>"
                    |path: "<<path>>"
                    |---
                    |""".stripMargin
  }
}

class Hugo(blocker: Blocker) {
  def buildPage[F[_]: Applicative: Sync: ContextShift](file: File, instant: Instant, target: java.io.File): F[Unit] = {
    val path = Paths.get("/images").resolve(file.fileName)
    val variables = Map(
      "title" -> file.fileName,
      "date" -> instant.toString,
      "path" -> path.toString()
    )
    val content = Templating.substitute(
      Hugo.ContentTemplates.photo,
      variables
    )
    val fileFlags = List(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    val toFile = fs2.io.file.writeAll(target.toPath(), blocker, flags = fileFlags)
    fs2
      .Stream(content)
      .through(fs2.text.utf8Encode)
      .through(toFile)
      .compile
      .drain
  }
}

object Templating {
  def substitute(template: String, items: Map[String, String]): String = {
    items.foldLeft(template) {
      case (template, (key, value)) =>
        template.replace(s"<<$key>>", value)
    }
  }
}
