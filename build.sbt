name := "FinchTest"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % "0.22.0",
  "com.github.finagle" %% "finch-circe" % "0.25.0",
  "io.circe" %% "circe-parser" % "0.9.0",
  "io.circe" %% "circe-generic" % "0.9.0"
)
