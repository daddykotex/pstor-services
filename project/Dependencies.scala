import sbt._

object Dependencies {
  val sttpVersion = "2.2.1"
  val circeVersion = "0.13.0"

  val sttpCore = "com.softwaremill.sttp.client" %% "core" % sttpVersion
  val sttpFS2 = "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % sttpVersion
  val sttpCirce = "com.softwaremill.sttp.client" %% "circe" % sttpVersion

  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val catsCore = "org.typelevel" %% "cats-core" % "2.1.1"
  val decline = "com.monovore" %% "decline-effect" % "1.2.0"
}
