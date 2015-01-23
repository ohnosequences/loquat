Nice.scalaProject

name         := "nisperito"
description  := "abstract nispero with small tasks"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion        := "2.10.4"
// crossScalaVersions  := Seq("2.10.4", "2.11.5")

resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4",
  "com.lihaoyi" %% "upickle" % "0.2.5",
  "org.scalamacros" %% s"quasiquotes" % "2.0.0" % "provided",
  // "net.liftweb" %% "lift-json" % "2.5.1",
  // "com.bacoder.jgit" % "org.eclipse.jgit" % "3.1.0-201309071158-r",
  // "org.scala-sbt" % "launcher-interface" % "0.13.0" % "provided",
  "net.databinder" %% "unfiltered-filter" % "0.6.8",
  "net.databinder" %% "unfiltered-jetty" % "0.6.8",
  "org.clapper" %% "avsl" % "1.0.1",
  "ohnosequences" %% "aws-scala-tools" % "0.10.0",
  "ohnosequences" %% "statika" % "1.0.0",
  "ohnosequences" %% "aws-statika" % "1.0.1",
  "ohnosequences" %% "amazon-linux-ami" % "0.14.0"
)

dependencyOverrides ++= Set(
  "commons-codec" % "commons-codec" % "1.6",
  "ohnosequences" %% "aws-statika" % "1.0.1"
)
