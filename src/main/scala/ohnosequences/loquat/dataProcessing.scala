package ohnosequences.loquat

case object dataProcessing {

  import utils._
  import ohnosequences.datasets._, dataSets._, fileLocations._

  import ohnosequences.cosas._, types._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._
  import ohnosequences.statika.results._

  import upickle.Js
  import java.io.File


  trait AnyProcessingContext {

    val  workingDir: file

    type DataSet <: AnyDataSet
    val  dataSet: DataSet

    type DataFiles  = DataSet#LocationsAt[FileDataLocation]
    val  dataFiles: DataFiles

    /* user can get the file corresponding to the given data key */
    def file[K <: AnyData](key: K)(implicit
        lookup: DataFiles Lookup (K := FileDataLocation)
      ): file = lookup(dataFiles).value.location

    /* or create a file instance in the orking directory */
    def /(name: String): file = workingDir / name
  }

  case class ProcessingContext[D <: AnyDataSet](
    val dataSet: D,
    val dataFiles: D#LocationsAt[FileDataLocation],
    val workingDir: file
  ) extends AnyProcessingContext { type DataSet = D }



  trait AnyDataProcessingBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    type InputFiles  = Input#LocationsAt[FileDataLocation]
    type OutputFiles = Output#LocationsAt[FileDataLocation]

    // should be provided implicitly:
    val parseInputFiles: ParseDenotations[InputFiles, File]
    val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

    type Context = ProcessingContext[Input]

    /* this is where user describes how to process each dataMapping:
       - it takes input data file locations
       - it must produce same for the output files */
    def processData(
      dataMappingId: String,
      context: Context
    ): Instructions[OutputFiles]


    /* This is a cover-method, which will be used in the worker run-loop */
    final def processFiles(
      dataMappingId: String,
      inputFilesMap: Map[String, File],
      workingDir: File
    ): Result[Map[String, File]] = {

      /* This method serialises OutputFiles data mapping to a normal Map */
      def filesMap(filesSet: OutputFiles): Map[String, File] =
        outputFilesToMap(filesSet).map { case (data, loc) =>
          data.label -> loc.location
        }

      parseInputFiles(inputFilesMap) match {
        case Left(err) => Failure(err.toString)
        case Right(inputFiles) => {
          processData(
            dataMappingId,
            ProcessingContext(input, inputFiles, workingDir)
          ).run(workingDir) match {
            case Failure(tr) => Failure(tr)
            case Success(tr, of) => Success(tr, filesMap(of))
          }
        }
      }

    }
  }

  abstract class DataProcessingBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  )(implicit
    val parseInputFiles: ParseDenotations[I#LocationsAt[FileDataLocation], File],
    val outputFilesToMap: ToMap[O#LocationsAt[FileDataLocation], AnyData, FileDataLocation]
  ) extends Bundle(deps: _*) with AnyDataProcessingBundle {

    type Input = I
    type Output = O
  }

}
