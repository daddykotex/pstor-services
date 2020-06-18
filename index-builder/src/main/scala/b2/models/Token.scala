package b2.models

import io.circe._, io.circe.generic.semiauto._

/**
  *
  {
    "absoluteMinimumPartSize": 5000000,
    "accountId": "205ebc40c789",
    "allowed": {
      "bucketId": "22a0f5be0bdc74507c170819",
      "bucketName": "dfrancoeur-photos",
      "capabilities": [
        "listBuckets",
        "listFiles",
        "readFiles",
        "shareFiles",
        "writeFiles",
        "deleteFiles"
      ],
      "namePrefix": null
    },
    "apiUrl": "https://api001.backblazeb2.com",
    "authorizationToken": "4_001205ebc40c7890000000001_0194f48b_a24911_acct_Aru1nDrw1rX6W7h9HGiN6pXjrdA=",
    "downloadUrl": "https://f001.backblazeb2.com",
    "recommendedPartSize": 100000000
  }
  *
  */
final case class TokenAllowed(bucketId: String, bucketName: String, capabilities: List[String], namePrefix: Option[String])
final case class TokenResponse(
    absoluteMinimumPartSize: Int,
    accountId: String,
    allowed: TokenAllowed,
    apiUrl: String,
    authorizationToken: String,
    downloadUrl: String,
    recommendedPartSize: Int
)

object TokenResponse {
  implicit val tokenAllowedDecoder: Decoder[TokenAllowed] = deriveDecoder
  implicit val tokenResponseDecoder: Decoder[TokenResponse] = deriveDecoder
}
