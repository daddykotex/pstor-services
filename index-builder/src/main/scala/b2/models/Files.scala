package b2.models

import io.circe._
import io.circe.generic.semiauto._

final case class ListFileNameRequest(bucketId: String, startFileName: Option[String])
object ListFileNameRequest {
  implicit val listFileNameRequestEncoder: Encoder[ListFileNameRequest] = deriveEncoder
}
final case class File(fileId: String, fileName: String, contentLength: Long, contentType: String, contentSha1: String)
object File {
  implicit val fileDecoder: Decoder[File] = deriveDecoder
}

final case class ListFileResponse(files: List[File], nextFileName: Option[String])
object ListFileResponse {
  implicit val listFileResponseDecoder: Decoder[ListFileResponse] = deriveDecoder
}
