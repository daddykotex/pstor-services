package models

sealed trait FileStatus extends Product with Serializable {
  val value: String
}
object FileStatus {
  import doobie._
  case object Pending extends FileStatus {
    override val value: String = "pending"
  }

  def unsafeParse(value: String): FileStatus = {
    value match {
      case Pending.value => Pending
      case x             => throw new IllegalArgumentException(s"Unexpected '$x' value for a FileStatus")
    }
  }

  implicit val fileStatusMeta: Meta[FileStatus] = Meta[String].timap(unsafeParse)(_.value)
}

final case class File(fileId: String, fileName: String, contentLength: Long, contentSha1: String, status: FileStatus)
object File {
  def pending(b2File: b2.models.File): File = {
    File(b2File.fileId, b2File.fileName, b2File.contentLength, b2File.contentSha1, FileStatus.Pending)
  }
}
