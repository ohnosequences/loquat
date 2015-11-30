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

  case class inputData(sample: Sample) extends DataSet(reads1(sample) :×: reads2(sample) :×: |[AnyData])
  case class outputData(sample: Sample) extends DataSet(stats :×: mergedReads(sample) :×: |[AnyData])

  case class processingBundle(sample: Sample) extends DataProcessingBundle()(
    inputData(sample),
    outputData(sample)
  ) {

    def instructions: AnyInstructions = say("horay!")

    def process(context: ProcessingContext[Input]): Instructions[OutputFiles] = {
      success("foo",
        stats.inFile(File("foo.bar.txt")) ::
        mergedReads(sample).inFile(File(".")) ::
        *[AnyDenotation { type Value = FileDataLocation }]
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
