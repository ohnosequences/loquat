Nice.scalaProject

name         := "nisperito"
description  := "abstract nispero with small tasks"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  // file/stream utils:
  "commons-io" % "commons-io" % "2.4",
  // logging:
  "org.clapper" %% "avsl" % "1.0.2",
  // json:
  "com.lihaoyi" %% "upickle" % "0.3.4",
  // internal structure:
  "ohnosequences" %% "statika"     % "2.0.0-SNAPSHOT",
  "ohnosequences" %% "aws-statika" % "2.0.0-SNAPSHOT",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.12.0"
)

dependencyOverrides ++= Set(
  "jline" % "jline" % "2.12.1"
)

wartremoverErrors in (Compile, compile) := Seq()
