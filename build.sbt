lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "ch.epfl.scala",
        scalaVersion := "2.13.3"
      )
    ),
    name := "pstor-root"
  )
  .aggregate(indexBuilder, server)

lazy val indexBuilder = (project in file("index-builder")).settings(
  name := "index-builder",
  libraryDependencies ++= Seq(
    Dependencies.decline,
    Dependencies.catsCore,
    Dependencies.sttpCore,
    Dependencies.sttpCirce,
    Dependencies.sttpFS2,
    Dependencies.circeGeneric
  )
)

lazy val server = (project in file("server")).settings(
  name := "server",
  libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"
)
