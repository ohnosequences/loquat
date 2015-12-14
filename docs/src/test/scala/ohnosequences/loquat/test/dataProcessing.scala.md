
```scala
package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, test.data._
import ohnosequences.statika.instructions._
import ohnosequences.datasets._, FileDataLocation._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object dataProcessing {

  case object inputData extends DataSet(matrix :×: |[AnyData])
  case object outputData extends DataSet(transposed :×: |[AnyData])

  case object processingBundle extends DataProcessingBundle()(
    inputData,
    outputData
  ) {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles } = {

      val outFile: File = context / "transposed.txt"

      LazyTry {
        val matrixRows = context.inputFile(matrix).lines
        val trans = matrixRows.map{ _.reverse }.toList.reverse
        outFile.createIfNotExists().overwrite(trans.mkString("\n"))
      } -&-
      success("transposed",
        transposed.inFile(outFile) ::
        *[AnyDenotation { type Value = FileDataLocation }]
      )
    }
  }

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