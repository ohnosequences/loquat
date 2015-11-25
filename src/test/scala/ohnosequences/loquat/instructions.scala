package ohnosequences.loquat.test

import ohnosequences.loquat._
import ohnosequences.statika.instructions._
import ohnosequences.datasets._, FileDataLocation._
import ohnosequences.cosas._, klists._, types._, records._
import better.files._

case object instructionsExample {

  // inputs:
  case object SomeData extends AnyDataType { val label = "someLabel" }
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")

  case object inputData extends DataSet(sample :×: fastq :×: |[AnyData])
  case object outputData extends DataSet(stats :×: results :×: |[AnyData])
  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

  implicit val f: fastq.type = fastq

  // instructions:
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

class buhh extends org.scalatest.FunSuite {

  test("files md5") {

    // import java.util.Base64
    // import java.nio.charset.StandardCharsets
    //
    // val file = File("buh")
    // file << "hola scala"
    // println { Base64.getEncoder.encodeToString( file.digest("MD5") ) }
    // println { file.digest("MD5") }
    //
    // println { file.md5 }
    // println { file.md5.toLowerCase }
    //
    // println { Base64.getEncoder.encodeToString(file.md5.getBytes(StandardCharsets.UTF_8)) }
    // println { Base64.getEncoder.encodeToString(file.md5.toLowerCase.getBytes(StandardCharsets.UTF_8)) }

  }
}
