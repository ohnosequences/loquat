
```scala
package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3DataLocation._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val input = S3Folder("loquat.testing", "input")
  val output = S3Folder("loquat.testing", "output")

  val dataMapping = DataMapping(processingBundle)(
    remoteInput =
      matrix.inS3(input / matrix.label) ::
      *[AnyDenotation { type Value = S3DataLocation }],
    remoteOutput =
      transposed.inS3(output / transposed.label) ::
      *[AnyDenotation { type Value = S3DataLocation }]
  )

}

```




[main/scala/ohnosequences/loquat/configs.scala]: ../../../../../main/scala/ohnosequences/loquat/configs.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../../main/scala/ohnosequences/loquat/dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../../../../../main/scala/ohnosequences/loquat/dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../../../../../main/scala/ohnosequences/loquat/logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../../../../../main/scala/ohnosequences/loquat/loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../../../../../main/scala/ohnosequences/loquat/manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../../../../../main/scala/ohnosequences/loquat/terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../../../../../main/scala/ohnosequences/loquat/utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../../../../../main/scala/ohnosequences/loquat/worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: md5.scala.md