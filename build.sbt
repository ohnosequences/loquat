name         := "loquat"
description  := "🍋"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  // logging:
  "ch.qos.logback"              % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.7.2",
  // AWS:
  "ohnosequences" %% "aws-scala-tools" % "0.18.1",
  // internal structure:
  "ohnosequences" %% "cosas"       % "0.8.0",
  "ohnosequences" %% "statika"     % "2.0.0-5-g2d4b05c",
  "ohnosequences" %% "datasets"    % "0.4.1"
)


// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()
wartremoverErrors in (Test,    compile) := Seq()


generateStatikaMetadataIn(Test)

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value
