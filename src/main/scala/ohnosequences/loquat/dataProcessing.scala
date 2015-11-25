package ohnosequences.loquat

import ohnosequences.datasets._

import ohnosequences.cosas._, types._, records._, fns._, klists._
// import ops.typeSets._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.results._

import upickle.Js
import better.files._


trait AnyProcessingContext {

  val  workingDir: File

  type DataSet <: AnyDataSet
  val  dataSet: DataSet

  type DataFiles <: DataSet#Raw
  val  dataFiles: DataFiles

  /* user can get the file corresponding to the given data key */
  def file[K <: AnyData](key: K)(implicit
      lookup: AnyApp1At[FindS[AnyDenotationOf[K] { type Value = File }], DataFiles] { type Y = K := File }
    ): File = lookup(dataFiles).value

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

// TODO predicate on DV for all of them being files?
case class ProcessingContext[D <: AnyDataSet, DV <: DataSet#Raw with AnyKList.Of[AnyDenotation { type Value = File }]](
  val dataSet: D,
  val dataFiles: DV,
  val workingDir: File
)
extends AnyProcessingContext { type DataSet = D }


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type InputFiles  <: Input#Raw with AnyKList.Of[AnyDenotation { type Value = File }]
  type OutputFiles <: Output#Raw with AnyKList.Of[AnyDenotation { type Value = File }]

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
        ).run(workingDir.toJava) match {
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
