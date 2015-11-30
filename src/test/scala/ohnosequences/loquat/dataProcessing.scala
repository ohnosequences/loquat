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

  case object inputData extends DataSet(sample :×: fastq :×: |[AnyData])
  case object outputData extends DataSet(stats :×: results :×: |[AnyData])

  case object instructs extends DataProcessingBundle[
    DataSet[|[AnyData]],
    DataSet[stats.type :×: results.type :×: |[AnyData]]
  ]() {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input]): Instructions[OutputContext] = {
      success("foo",
        (outputData,
          stats.inFile(File("foo.bar")) ::
          results.inFile(File(".")) ::
          *[AnyDenotation { type Value = FileDataLocation }]
        )
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
