package ohnosequences.loquat.test

object instructionsExample {

  import ohnosequences.loquat._, dataProcessing._
  import ohnosequences.statika.instructions._
  import ohnosequences.datasets._, dataSets._, fileLocations._
  import ohnosequences.cosas._, typeSets._
  import better.files._


  // inputs:
  case object SomeData extends AnyDataType { val label = "someLabel" }
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")

  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

  // instructions:
  case object instructs extends DataProcessingBundle()(
    input = sample :^: fastq :^: DNil,
    output = stats :^: results :^: DNil
  ) {

    def install: AnyInstructions = say("horay!")

    def processDataMapping(dataMappingId: String, workingDir: File): Instructions[OutputFiles] = {
      success("foo",
        stats.inFile(File(dataMappingId)) :~:
        results.inFile(File(".")) :~:
        ∅
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
