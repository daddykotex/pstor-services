package b2.models

import io.circe._
import io.circe.generic.semiauto._

final case class Bucket(
    accountId: String,
    bucketId: String,
    bucketInfo: Map[String, String], // lack of a better type
    bucketName: String,
    bucketType: String,
    // lack of a better type, see https://www.backblaze.com/b2/docs/cors_rules.html
    corsRules: List[Map[String, String]],
    lifecycleRules: List[String],
    options: List[String],
    revision: Int
)

/**
  {
    "buckets": [
      {
        "accountId": "205ebc40c789",
        "bucketId": "22a0f5be0bdc74507c170819",
        "bucketInfo": {},
        "bucketName": "dfrancoeur-photos",
        "bucketType": "allPrivate",
        "corsRules": [],
        "lifecycleRules": [],
        "options": [],
        "revision": 2
      }
    ]
  }
  */
final case class BucketResponse(buckets: List[Bucket])
object BucketResponse {
  implicit val bucketDecoder: Decoder[Bucket] = deriveDecoder
  implicit val bucketResponseDecoder: Decoder[BucketResponse] = deriveDecoder
}
