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
  "com.lihaoyi" %% "upickle" % "0.3.5",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.7.1",
  "ohnosequences" %% "statika"     % "2.0.0-M4",
  "ohnosequences" %% "aws-statika" % "2.0.0-SNAPSHOT",
  "ohnosequences" %% "datasets"    % "0.2.0-SNAPSHOT",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.15.0-SNAPSHOT"
)

dependencyOverrides ++= Set(
  "ohnosequences" %% "aws-statika" % "2.0.0-SNAPSHOT",
  "ohnosequences" %% "aws-scala-tools" % "0.15.0-SNAPSHOT",
  // "ohnosequences" %% "aws-scala-tools" % "0.14.0",
  "jline" % "jline" % "2.12.1"
)

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
