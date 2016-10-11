name         := "loquat"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  // logging:
  "ch.qos.logback"              % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.1.0",
  // json:
  "com.lihaoyi" %% "upickle" % "0.3.6",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.8.0",
  "ohnosequences" %% "statika"     % "2.0.0-M5-4-gb18d01a",
  "ohnosequences" %% "datasets"    % "0.3.0-7-g9bb7220",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.17.0",
  // files:
  "com.github.pathikrit" %% "better-files" % "2.13.0",
  // testing
  "org.scalatest"  %% "scalatest" % "2.2.6" % Test
)

dependencyOverrides ++= Set(
  "ohnosequences" %% "aws-scala-tools" % "0.17.0-31-g17e7006",
  "org.slf4j" % "slf4j-api" % "1.7.20"
)

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
wartremoverErrors in (Test,    compile) := Seq()


generateStatikaMetadataIn(Test)

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value

// This turns on fat-jar publishing during release process:
publishFatArtifact in Release := true
