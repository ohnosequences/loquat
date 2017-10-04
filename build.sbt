name         := "loquat"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

crossScalaVersions := Seq("2.11.11", "2.12.3")
scalaVersion  := crossScalaVersions.value.last

libraryDependencies ++= Seq(
  // Internal:
  "ohnosequences" %% "aws-statika"     % "2.0.0",
  "ohnosequences" %% "datasets"        % "0.5.1",
  // Logging:
  "ch.qos.logback"              % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.7.2",
  // Testing
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)

wartremoverErrors in (Compile, compile) --= Seq(
  Wart.TryPartial
)

generateStatikaMetadataIn(Test)

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value
