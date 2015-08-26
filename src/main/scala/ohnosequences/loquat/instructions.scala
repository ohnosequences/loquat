package ohnosequences.loquat

case object instructions {

  import ohnosequences.loquat._
  import ohnosequences.datasets._, dataSets._, fileLocations._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._, ops.types._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._
  import ohnosequences.statika.results._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  trait AnyInstructionsBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    type InputFiles  = Input#LocationsAt[FileDataLocation]
    type OutputFiles = Output#LocationsAt[FileDataLocation]

    // should be provided implicitly:
    val parseInputFiles: ParseDenotations[InputFiles, File]
    val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

    /* This method serialises OutputFiles data mapping to a normal Map */
    def filesMap(filesSet: OutputFiles): Map[String, File] =
      outputFilesToMap(filesSet).map { case (data, loc) =>
        data.label -> loc.location
      }

    /* this is where user describes instructions how to process each dataMapping:
       - it takes input data file locations
       - it must produce same for the output files */
    def processData(
      dataMappingId: String,
      inputFiles: InputFiles
    ): AnyInstructions.withOut[OutputFiles]

    /* This is a cover-method, which will be used in the worker run-loop */
    final def processFiles(
      dataMappingId: String,
      inputFilesMap: Map[String, File],
      workingDir: File
    ): Result[Map[String, File]] = {

      parseInputFiles(inputFilesMap) match {
        case (Left(err), _) => Failure(err.toString)
        case (Right(inputFiles), _) => {
          processData(dataMappingId, inputFiles).run(workingDir) match {
            case Failure(tr) => Failure(tr)
            case Success(tr, of) => Success(tr, filesMap(of))
          }
        }
      }

    }
  }

  abstract class InstructionsBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  )(implicit
    val parseInputFiles: ParseDenotations[I#LocationsAt[FileDataLocation], File],
    val outputFilesToMap: ToMap[O#LocationsAt[FileDataLocation], AnyData, FileDataLocation]
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type Input = I
    type Output = O
  }

}
