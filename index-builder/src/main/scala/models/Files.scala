package models

sealed trait FileStatus extends Product with Serializable {
  import FileStatus._

  val value: String = this match {
    case Pending        => "pending"
    case Downloading    => "downloading"
    case Downloaded     => "downloaded"
    case SiteProcessing => "site_processing"
    case SiteProcessed  => "site_processed"
    case Error          => "error"
  }
}

/**
  * Pending => Downloading => SiteProcessing => Processed
  */
object FileStatus {
  import doobie._

  case object Pending extends FileStatus
  case object Downloading extends FileStatus
  case object Downloaded extends FileStatus
  case object SiteProcessing extends FileStatus
  case object SiteProcessed extends FileStatus

  case object Error extends FileStatus

  def unsafeParse(value: String): FileStatus = {
    value match {
      case Pending.value        => Pending
      case Downloading.value    => Downloading
      case Downloaded.value     => Downloaded
      case SiteProcessing.value => SiteProcessing
      case SiteProcessed.value  => SiteProcessed
      case Error.value          => Error
      case x                    => throw new IllegalArgumentException(s"Unexpected '$x' value for a FileStatus")
    }
  }

  implicit val fileStatusMeta: Meta[FileStatus] = Meta[String].timap(unsafeParse)(_.value)
}

final case class File(fileId: String, fileName: String, contentLength: Long, contentType: String, contentSha1: String, status: FileStatus)
object File {
  def pending(b2File: b2.models.File): File = {
    File(b2File.fileId, b2File.fileName, b2File.contentLength, b2File.contentType, b2File.contentSha1, FileStatus.Pending)
  }
  def stripExtension(filename: String): String = filename.take(filename.lastIndexOf('.'))
}
