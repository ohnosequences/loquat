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

  type DataFiles <: DataSet#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }]
  val  dataFiles: DataFiles

  /* user can get the file corresponding to the given data key */
  def file[K <: AnyData](key: K)(implicit
      lookup: AnyApp1At[FindS[AnyDenotationOf[K] { type Value = FileDataLocation }], DataFiles] { type Y = K := FileDataLocation }
    ): File = lookup(dataFiles).value.location

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

// TODO predicate on DV for all of them being files?
case class ProcessingContext[D <: AnyDataSet, DV <: D#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }]](
  val dataSet: D,
  val dataFiles: DV,
  val workingDir: File
)
extends AnyProcessingContext {

  type DataSet = D
  type DataFiles = DV
}


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type InputFiles  <: Input#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }]
  type OutputFiles <: Output#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }]

  // should be provided implicitly:
  val parseInputFiles: AnyApp1At[FileDataLocation ParseDenotations Input#Keys, Map[String,FileDataLocation]] { type Y = Either[ParseDenotationsError, InputFiles] } // ParseDenotations[InputFiles, File]
  // val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

  type Context = ProcessingContext[Input, InputFiles]

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
      (input.keys.types.asList.map{ _.label }) zip
      (filesSet.asList.map{ _.value.location }) toMap

    parseInputFiles(inputFilesMap mapValues { f => FileDataLocation(f) }) match {
      case Left(err) => Failure(err.toString)
      case Right(inputFiles) => {
        processData(
          dataMappingId,
          ProcessingContext[Input, InputFiles](input, inputFiles, workingDir)
        ).run(workingDir.toJava) match {
          case Failure(tr) => Failure(tr)
          case Success(tr, of) => Success(tr, filesMap(of))
        }
      }
    }
  }
}

// case object AnyDataProcessingBundle {
//
//   implicit def parseFromFileDataLocation[D <: AnyData](implicit data: D): DenotationParser[D, FileDataLocation, FileDataLocation] =
//     new DenotationParser(data, data.label)({ f => Some(f) })
// }

abstract class DataProcessingBundle[
  I <: AnyDataSet, IF <: I#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }],
  O <: AnyDataSet, OF <: O#Raw with AnyKList.withBound[AnyDenotation { type Value = FileDataLocation }]
](deps: AnyBundle*)(
  val input: I,
  val output: O
)(implicit
  val parseInputFiles: AnyApp1At[FileDataLocation ParseDenotations I#Keys, Map[String,FileDataLocation]] { type Y = Either[ParseDenotationsError, IF] }
) extends Bundle(deps: _*) with AnyDataProcessingBundle {

  type Input = I
  type InputFiles = IF
  type Output = O
  type OutputFiles = OF
}
