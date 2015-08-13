package ohnosequences.nisperito.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.nisperito._, pipas._, bundles._, instructions._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.records._
  import java.io.File


  // inputs:
  case object sample extends InputKey("sample")
  case object fastaq extends InputKey("fastaq")

  // outputs:
  case object stats extends OutputKey
  case object results extends OutputKey

  // instructions:
  case object instructs extends InstructionsBundle()(
    inputKeys = sample :~: fastaq :~: ∅,
    outputKeys = stats :~: results :~: ∅
  ) {

    def install: Results = success("horay!")

    def processPipa(pipaId: String, workingDir: File): (Results, OutputFiles) = {
      val files =
        Files(
          stats(new File(pipaId)) :~:
          results(sample.file(workingDir)) :~:
          ∅
        )
      (success("foo"), files)
    }
  }

  val outputs = instructs.processPipa("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the outputKeys by construction
  val resultsFile: File = outputs.filesMap(results.label)

}
