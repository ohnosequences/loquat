
```scala
package ohnosequences.loquat.test

object dataMappingsExample {

  import ohnosequences.awstools.s3._
  import ohnosequences.loquat._, dataMappings._
  import ohnosequences.datasets._, s3Locations._
  import ohnosequences.cosas._, typeSets._
  import instructionsExample._


  val dataMapping = DataMapping(id = "dataMapping3498734", dataProcessing = instructs)(
    remoteInput =
      sample.inS3(S3Object("bucket", "key")) :~:
      fastq.inS3(S3Object("bucket", "key")) :~:
      ∅,
    remoteOutput =
      stats.inS3(S3Object("bucket", "key")) :~:
      results.inS3(S3Object("bucket", "key")) :~:
      ∅
  )

}

```




[test/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/loquat/instructions.scala]: instructions.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../../../../main/scala/ohnosequences/loquat/dataProcessing.scala.md
[main/scala/ohnosequences/loquat/workers.scala]: ../../../../main/scala/ohnosequences/loquat/workers.scala.md
[main/scala/ohnosequences/loquat/managers.scala]: ../../../../main/scala/ohnosequences/loquat/managers.scala.md
[main/scala/ohnosequences/loquat/daemons.scala]: ../../../../main/scala/ohnosequences/loquat/daemons.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../../../../main/scala/ohnosequences/loquat/loquats.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../../../../main/scala/ohnosequences/loquat/utils.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../main/scala/ohnosequences/loquat/dataMappings.scala.md
[main/scala/ohnosequences/loquat/configs.scala]: ../../../../main/scala/ohnosequences/loquat/configs.scala.md