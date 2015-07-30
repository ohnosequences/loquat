Nice.scalaProject

name         := "nisperito"
description  := "abstract nispero with small tasks"
organization := "ohnosequences"
bucketSuffix := "era7.com"

scalaVersion        := "2.11.7"
// crossScalaVersions  := Seq("2.10.5", "2.11.7")

resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4",
  "com.lihaoyi" %% "upickle" % "0.3.4",
  "net.databinder" %% "unfiltered-filter" % "0.8.4",
  "net.databinder" %% "unfiltered-jetty" % "0.8.4",
  "org.clapper" %% "avsl" % "1.0.2",
  "ohnosequences" %% "aws-scala-tools" % "0.12.0",
  "ohnosequences" %% "statika" % "2.0.0-SNAPSHOT",
  "ohnosequences" %% "aws-statika" % "2.0.0-SNAPSHOT"
)

dependencyOverrides ++= Set(
  "commons-codec" % "commons-codec" % "1.6",
  "jline" % "jline" % "2.12.1",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4"
)

// wartremoverErrors := Seq()
wartremoverErrors in (Compile, compile) := Seq()
