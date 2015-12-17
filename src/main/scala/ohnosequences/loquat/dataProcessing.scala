package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, types._, typeUnions._, records._, fns._, klists._
// import ops.typeSets._

import ohnosequences.statika._

import upickle.Js
import better.files._


trait AnyProcessingContext {

  val workingDir: File
  val inputDir: File

  type DataSet <: AnyDataSet

  /* user can get the file corresponding to the given data key */
  def inputFile[K <: AnyData](key: K)(implicit
    isIn: K isOneOf DataSet#Keys#Types#AllTypes
  ): File = inputDir / key.label

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

case class ProcessingContext[D <: AnyDataSet](
  val workingDir: File,
  val inputDir: File
) extends AnyProcessingContext { type DataSet = D }


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type OutputFiles = DataSetLocations[Output, FileDataLocation]

  // this is where you define what to do
  def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles }


  final def runProcess(workingDir: File, inputDir: File): Result[Map[String, File]] = {
    process(ProcessingContext[Input](workingDir, inputDir))
      .run(workingDir.toJava) match {
        case Failure(tr) => Failure(tr)
        case Success(tr, of) => Success(tr, toMap(of))
      }
  }

}

abstract class DataProcessingBundle[
  I <: AnyDataSet,
  O <: AnyDataSet
](deps: AnyBundle*)(
  val input: I,
  val output: O
) extends Bundle(deps: _*) with AnyDataProcessingBundle {

  type Input = I
  type Output = O
}
