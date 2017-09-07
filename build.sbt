name         := "loquat"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  // logging:
  "ch.qos.logback"              % "logback-classic" % "1.1.8",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0",
  // json:
  "com.lihaoyi" %% "upickle" % "0.4.4",
  // AWS:
  "ohnosequences" %% "aws-scala-tools" % "0.18.1",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.8.0",
  "ohnosequences" %% "statika"     % "2.0.0",
  "ohnosequences" %% "datasets"    % "0.4.1"
)

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
wartremoverErrors in (Test,    compile) := Seq()


generateStatikaMetadataIn(Test)

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value

// This turns on fat-jar publishing during release process:
// publishFatArtifact in Release := true
