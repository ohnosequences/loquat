package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, test.data._, utils._
import ohnosequences.statika._
import ohnosequences.datasets._, FileResource._
import ohnosequences.cosas._, klists._, types._, records._
import concurrent.duration._
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
        sleep(2.minutes + 20.seconds)
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
