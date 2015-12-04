package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, types._, typeUnions._, records._, fns._, klists._
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

  /* user can get the file corresponding to the given data key */
  def file[K <: AnyData](key: K)(implicit
    isIn: K isOneOf DataSet#Keys#Types#Hola
  ): File = workingDir / "input" / key.label

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

// TODO predicate on DV for all of them being files?
case class ProcessingContext[
  D <: AnyDataSet
](val dataSet: D,
  val workingDir: File
) extends AnyProcessingContext {
  type DataSet = D
}


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type OutputFiles = DataSetLocations[Output, FileDataLocation]

  // this is where you define what to do
  def process(context: ProcessingContext[Input]): Instructions[OutputFiles]


  final def runProcess(workingDir: File): Result[Map[String, File]] = {
    process(ProcessingContext(input, workingDir))
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
