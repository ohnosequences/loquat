
```scala
package ohnosequences.loquat.test

object dataMappingsExample {

  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.loquat._, dataMappings._
  import ohnosequences.datasets._, s3Locations._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import instructionsExample._


  val dataMapping = DataMapping(
    id = "dataMapping3498734",
    instructions = instructs,
    remoteInput =
      sample.atS3(ObjectAddress("", "")) :~:
      fastq.atS3(ObjectAddress("", "")) :~:
      ∅,
    remoteOutput =
      stats.atS3(ObjectAddress("", "")) :~:
      results.atS3(ObjectAddress("", "")) :~:
      ∅
  )
}

```




[main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala.md
[main/scala/ohnosequences/nisperito/Config.scala]: ../../../../main/scala/ohnosequences/nisperito/Config.scala.md
[main/scala/ohnosequences/nisperito/dataMappings.scala]: ../../../../main/scala/ohnosequences/nisperito/dataMappings.scala.md
[main/scala/ohnosequences/nisperito/Nisperito.scala]: ../../../../main/scala/ohnosequences/nisperito/Nisperito.scala.md
[main/scala/ohnosequences/nisperito/Utils.scala]: ../../../../main/scala/ohnosequences/nisperito/Utils.scala.md
[test/scala/ohnosequences/nisperito/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/nisperito/instructions.scala]: instructions.scala.md