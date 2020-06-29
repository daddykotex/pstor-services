addCommandAlias("fixRun", "all compile:scalafix test:scalafix")
addCommandAlias("fixCheck", "all compile:scalafix --check test:scalafix --check")

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "ch.epfl.scala",
        scalaVersion := "2.13.2",
        // scala fix
        semanticdbEnabled := true,
        semanticdbVersion := scalafixSemanticdb.revision,
        scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.3.1-RC3"
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
    Dependencies.circeGeneric,
    Dependencies.doobieCore,
    Dependencies.doobieHikari,
    Dependencies.sqlite
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
)

lazy val server = (project in file("server")).settings(
  name := "server",
  libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"
)
