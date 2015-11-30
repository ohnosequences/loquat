package ohnosequences.loquat

import utils._

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

  type DataFiles <: FileLocations
  val  dataFiles: DataFiles

  /* user can get the file corresponding to the given data key */
  def file[K <: AnyData](key: K)(implicit
      lookup: AnyApp1At[
        FindS[AnyDenotationOf[K] { type Value = FileDataLocation }],
        DataFiles
      ] { type Y = K := FileDataLocation }
    ): File = lookup(dataFiles).value.location

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

// TODO predicate on DV for all of them being files?
case class ProcessingContext[
  DF <: FileLocations
](val dataFiles: DF,
  val workingDir: File
) extends AnyProcessingContext {
  type DataFiles = DF
}


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type InputFiles  = FileLocationsFor[Input]
  type OutputFiles = FileLocationsFor[Output]

  // should be provided implicitly:
  val parseInputFiles: AnyApp1At[
    FileDataLocation ParseDenotations Input#Keys,
    Map[String,FileDataLocation]
  ] { type Y = Either[ParseDenotationsError, InputFiles] }

  type OutputContext = (Output, OutputFiles)

  // this is where you define what to do
  def process(context: ProcessingContext[InputFiles]): Instructions[OutputContext]

  final def runProcess(workingDir: File, inputFiles: Map[String,File]): Result[Map[String, File]] = {

    // TODO move to utils
    def outputAsMap(outCtx: OutputContext): Map[String, File] =
      (outCtx._1.keys.types.asList.map{ _.label }) zip
      (outCtx._2.asList.map { _.value.location }) toMap

    parseInputFiles(inputFiles mapValues { f => FileDataLocation(f) }) match {
      case Left(err) => Failure(err.toString)
      case Right(inputFiles) => {
        process(
          ProcessingContext[InputFiles](inputFiles, workingDir)
        ).run(workingDir.toJava) match {
          case Failure(tr) => Failure(tr)
          case Success(tr, of) => Success(tr, outputAsMap(of))
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
  val parseInputFiles: AnyApp1At[
    FileDataLocation ParseDenotations I#Keys,
    Map[String, FileDataLocation]
  ] { type Y = Either[ParseDenotationsError, FileLocationsFor[I]] }
) extends Bundle(deps: _*) with AnyDataProcessingBundle {

  type Input = I
  type Output = O

  // what's the point of instructions for this?
}
