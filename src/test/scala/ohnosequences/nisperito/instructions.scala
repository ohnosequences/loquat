package ohnosequences.nisperito.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.nisperito._, pipas._, bundles._, instructions._, dataSets._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import java.io.File


  // inputs:
  case object SomeData extends AnyDataType
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

    def processPipa(pipaId: String, workingDir: File): (Results, OutputFiles) = {
      val files =
        stats.inFile(new File(pipaId)) :~:
        results.inFile(new File("")) :~:
        ∅
      (success("foo"), files)
    }
  }

  val outputs = instructs.processPipa("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}
