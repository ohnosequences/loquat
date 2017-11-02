package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, utils.files._, test.data._
import ohnosequences.statika._
import ohnosequences.datasets._, FileResource._
import ohnosequences.cosas._, klists._, types._, records._
import concurrent.duration._

case object dataProcessing {

  case object inputData extends DataSet(prefix :×: text :×: matrix :×: |[AnyData])
  case object outputData extends DataSet(transposed :×: |[AnyData])

  case object processingBundle extends DataProcessingBundle()(
    inputData,
    outputData
  ) {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input]): AnyInstructions.withBound[OutputFiles] = {

      val txt: String  = context.inputFile(text).lines.mkString("\n")
      val prfx: String = context.inputFile(prefix).lines.mkString("\n")

      val outFile: File = (context / s"${prfx}.transposed.txt").createFile

      LazyTry {
        sleep(24.seconds)
        val matrixRows = context.inputFile(matrix).lines
        val trans = matrixRows.map{ _.reverse }.toList.reverse
        outFile.createFile.overwrite(trans.mkString("\n"))
        outFile.append(txt)
      } -&-
      success("transposed",
        transposed(outFile) ::
        Resources[FileResource]
      )
    }
  }

}
