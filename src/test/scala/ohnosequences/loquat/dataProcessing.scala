package ohnosequences.loquat.test

import ohnosequences.loquat._, test.data._
import ohnosequences.statika.instructions._
import ohnosequences.datasets._, FileDataLocation._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object dataProcessing {

  case object instructs extends DataProcessingBundle[
    inputData.type,
    (sample.type := FileDataLocation) :: (fastq.type := FileDataLocation) :: *[AnyDenotation { type Value = FileDataLocation }],
    outputData.type, (stats.type := FileDataLocation) :: (results.type := FileDataLocation) :: *[AnyDenotation { type Value = FileDataLocation }]
  ]()(
    inputData,
    outputData
  ) {

    def instructions: AnyInstructions = say("horay!")

    def processData(dataMappingId: String, context: Context): Instructions[OutputFiles] = {
      success("foo",
        stats.inFile(File(dataMappingId)) ::
        results.inFile(File(".")) ::
        *[AnyDenotation { type Value = FileDataLocation}]
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
