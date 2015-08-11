package ohnosequences.nisperito.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.nisperito._, tasks._, bundles._, instructions._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.records._
  import java.io.File


  // inputs:
  object sample extends InputKey
  object fastaq extends InputKey

  // outputs:
  object stats extends OutputKey
  object results extends OutputKey

  // instructions:
  case object instructs extends InstructionsBundle()(
    inputKeys = sample :~: fastaq :~: ∅,
    outputKeys = stats :~: results :~: ∅
  ) {

    def install: Results = success("horay!")

    def processTask: (Results, OutputFiles) = {
      val files =
        Files(
          stats(new File("")) :~:
          results(sample.file) :~:
          ∅
        )
      (success("foo"), files)
    }
  }

  val outputs = instructs.processTask._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the outputKeys by construction
  val resultsFile: File = outputs.filesMap(results.label)

}
