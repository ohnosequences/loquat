
```scala
package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, test.data._
import ohnosequences.statika._
import ohnosequences.datasets._, FileResource._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object dataProcessing {

  case object inputData extends DataSet(prefix :×: text :×: matrix :×: |[AnyData])
  case object outputData extends DataSet(transposed :×: |[AnyData])

  case object processingBundle extends DataProcessingBundle()(
    inputData,
    outputData
  ) {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {

      val txt: String = context.inputFile(text).contentAsString
      val prfx: String = context.inputFile(prefix).contentAsString

      val outFile: File = (context / s"${prfx}.transposed.txt").createIfNotExists()

      LazyTry {
        val matrixRows = context.inputFile(matrix).lines
        val trans = matrixRows.map{ _.reverse }.toList.reverse
        outFile.createIfNotExists().overwrite(trans.mkString("\n"))
        outFile.append(txt)
      } -&-
      success("transposed",
        transposed(outFile.toJava) ::
        *[AnyDenotation { type Value <: FileResource }]
      )
    }
  }

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