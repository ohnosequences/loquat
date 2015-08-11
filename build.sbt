Nice.scalaProject

name         := "nisperito"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  // logging:
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  // json:
  "com.lihaoyi" %% "upickle" % "0.3.4",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.7.0-SNAPSHOT",
  "ohnosequences" %% "statika"     % "2.0.0-SNAPSHOT",
  "ohnosequences" %% "aws-statika" % "2.0.0-SNAPSHOT",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.13.2"
)

dependencyOverrides += "jline" % "jline" % "2.12.1"

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
