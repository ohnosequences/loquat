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
