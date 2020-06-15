import sbt._

object Dependencies {
  val sttpCore = "com.softwaremill.sttp.client" %% "core" % "2.2.0"
  val sttpFS2 = "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.2.0"
  val catsCore = "org.typelevel" %% "cats-core" % "2.1.0"
  val decline = "com.monovore" %% "decline" % "1.0.0"
}