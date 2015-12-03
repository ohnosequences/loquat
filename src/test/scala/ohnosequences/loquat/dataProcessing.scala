package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, test.data._
import ohnosequences.statika.instructions._
import ohnosequences.datasets._, FileDataLocation._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object dataProcessing {

  implicit def fileParser[D <: AnyData](implicit d: D):
    DenotationParser[D, AnyDataLocation, FileDataLocation] =
    new DenotationParser(d, d.label)({ f: FileDataLocation => Some(f) })

  case object inputData extends DataSet(matrix :×: |[AnyData])
  case object outputData extends DataSet(transposed :×: |[AnyData])

  case object processingBundle extends DataProcessingBundle()(
    inputData,
    outputData
  ) {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input, IRaw]): Instructions[OutputFiles] = {

      val matrixRows = context.lookup(matrix)//(
        //FindS.foundInHead[AnyDenotation.Of[matrix.type], AnyDenotation.Of[matrix.type], *[AnyDenotation]]
      //.lines

      val trans = matrixRows.map{ _.reverse }.toList.reverse

      success("foo",
        transposed.inFile(File("foo.bar.txt")) ::
        *[AnyDenotation { type Value = FileDataLocation }]
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
