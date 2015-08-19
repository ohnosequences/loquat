package ohnosequences.nisperito.bundles

case object instructions {

  import ohnosequences.nisperito._, dataSets._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  trait AnyInstructionsBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    type OutputFiles = Output#LocationsAt[FileDataLocation]

    // should be provided implicitly:
    val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

    def filesMap(filesSet: OutputFiles): Map[String, File] =
      outputFilesToMap(filesSet).map { case (data, loc) =>
        data.label -> loc.location
      }

    /* this is where user describes instructions how to process each pipa:
       - it can assume that the input files are in place (`inputKey.file`)
       - it must produce output files declared in the pipa */
    def processPipa(pipaId: String, workingDir: File): (Results, OutputFiles)
  }

  abstract class InstructionsBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  )(implicit
    val outputFilesToMap: ToMap[O#LocationsAt[FileDataLocation], AnyData, FileDataLocation]
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type Input = I
    type Output = O
  }

}
