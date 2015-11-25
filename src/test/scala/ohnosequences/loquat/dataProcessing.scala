package ohnosequences.loquat.test

import ohnosequences.loquat._, test.data._
import ohnosequences.statika.instructions._
import ohnosequences.datasets._, FileDataLocation._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object dataProcessing {

  implicit def fileParser[D <: AnyData](implicit d: D): DenotationParser[D,AnyDataLocation, FileDataLocation] = new DenotationParser(d,d.label)( { v: FileDataLocation => Some(v) })

  case object instructs extends DataProcessingBundle()(
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
