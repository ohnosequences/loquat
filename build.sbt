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
  "ohnosequences" %% "cosas"       % "0.8.0",
  "ohnosequences" %% "statika"     % "2.0.0-M5",
  "ohnosequences" %% "datasets"    % "0.2.0",
  // amazon:
  "ohnosequences" %% "aws-scala-tools" % "0.16.0",
  // files:
  "com.github.pathikrit" %% "better-files" % "2.13.0",
  // testing
  "org.scalatest"  %% "scalatest" % "2.2.5" % Test
)


// dependencyOverrides ++= Set()

// FIXME: warts should be turn on back after the code clean up
wartremoverErrors in (Compile, compile) := Seq()

fatArtifactSettings

enablePlugins(BuildInfoPlugin)
buildInfoPackage := "generated.metadata"
buildInfoObject  := name.value.split("""\W""").map(_.capitalize).mkString
buildInfoOptions := Seq(BuildInfoOption.Traits("ohnosequences.statika.AnyArtifactMetadata"))
buildInfoKeys    := Seq[BuildInfoKey](
  organization,
  version,
  "artifact" -> name.value.toLowerCase,
  "artifactUrl" -> fatArtifactUrl.value
)

//// Uncomment for testing: ////

// // For including test code in the fat artifact:
// unmanagedSourceDirectories in Compile += (scalaSource in Test).value
