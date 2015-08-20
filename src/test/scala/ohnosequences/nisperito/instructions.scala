package ohnosequences.loquat.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.loquat._, dataMappings._, bundles._, instructions._, dataSets._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import java.io.File


  // inputs:
  case object SomeData extends AnyDataType { val label = "someLabel" }
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")

  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

  // instructions:
  case object instructs extends InstructionsBundle()(
    input = sample :^: fastq :^: DNil,
    output = stats :^: results :^: DNil
  ) {

    def install: Results = success("horay!")

    def processDataMapping(dataMappingId: String, workingDir: File): (Results, OutputFiles) = {
      val files =
        stats.inFile(new File(dataMappingId)) :~:
        results.inFile(new File("")) :~:
        ∅
      (success("foo"), files)
    }
  }

  val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
