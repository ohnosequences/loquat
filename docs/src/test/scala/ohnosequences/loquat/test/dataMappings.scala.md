
```scala
package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3Resource._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val input = S3Folder("loquat.testing", "input")
  val output = S3Folder("loquat.testing", "output")

  val dataMapping = DataMapping("foo", processingBundle)(
    remoteInput = Map(
      prefix -> MessageResource("viva-loquat"),
      text -> MessageResource("""bluh-blah!!!
      |foo bar
      |qux?
      |¡buh™!
      |""".stripMargin),
      matrix -> S3Resource(input / matrix.label)
    ),
    remoteOutput = Map(
      transposed -> S3Resource(output / transposed.label)
    )
  )

}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/awsClients.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/user.scala.md
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