lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "ch.epfl.scala",
      scalaVersion := "2.13.1"
    )),
    name := "pstor-root"
  )


lazy val indexBuilder = (project in file("index-builder")).
  settings(
    name := "index-builder",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"
  )

lazy val server = (project in file("server")).
  settings(
    name := "server",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"
  )