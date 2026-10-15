scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Xfatal-warnings",
  "-language:reflectiveCalls",
)

scalaVersion := "2.13.16"
val chiselVersion = "6.7.0"

addCompilerPlugin(("org.chipsalliance" % "chisel-plugin" % chiselVersion).cross(CrossVersion.full))
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
libraryDependencies += "com.lihaoyi" %% "upickle" % "3.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "BPU-gen"
  )
