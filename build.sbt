Nice.scalaProject

name         := "loquat"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  // logging:
  "ch.qos.logback"              % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.1.0",
  // json:
  "com.lihaoyi" %% "upickle" % "0.3.6",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.7.1",
  "ohnosequences" %% "statika"     % "2.0.0-M4",
  "ohnosequences" %% "aws-statika" % "2.0.0-M5",
  "ohnosequences" %% "datasets"    % "0.2.0-M1",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.15.0-M1",
  // files:
  "com.github.pathikrit" %% "better-files" % "2.13.0"
)

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
