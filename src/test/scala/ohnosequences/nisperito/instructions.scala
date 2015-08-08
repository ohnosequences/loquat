package ohnosequences.nisperito.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.nisperito._, tasks._, bundles._, instructions._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import java.io.File


  object sample extends S3InputKey
  object fastaq extends S3InputKey

  object stats extends S3OutputKey
  object results extends S3OutputKey
  object outFiles extends Record(stats :~: results :~: ∅)

  case object instructs extends InstructionsBundle()(
    inputKeys = sample :~: fastaq :~: ∅,
    outputKeys = stats :~: results :~: ∅
  ) {

    def install: Results = success("horay!")

    def processTask: (Results, OutputFiles) = {
      val files =
        Files(
          stats(new File("")) :~:
          results(new File("")) :~:
          ∅
        )
      (success("foo"), files)
    }
  }


}
