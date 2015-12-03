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

  type DataSet <: AnyDataSet

  type DataFiles <: DataSet#Raw
  val  dataFiles: DataFiles //Locations[DataSet, FileDataLocation]

  /* user can get the file corresponding to the given data key */
  // def file[K <: AnyData](key: K)(implicit
  //     lookup: AnyApp1At[
  //       find[K := FileDataLocation],
  //       DataFiles
  //       // DataSetLocations[DataSet, FileDataLocation]
  //     ] //{ type Y = K := FileDataLocation }
  //   ): File = lookup(dataFiles).value.location
  def lookup[K <: AnyData](key: K)(implicit
      l: AnyApp1At[
        FindS[AnyDenotation { type Tpe = K } ],
        DataFiles
      ] //{ type Y = AnyDenotation { type Value = AnyDataLocation } }
    ): K#Raw = (l(dataFiles): AnyDenotation { type Tpe = K } ).value

  /* or create a file instance in the orking directory */
  def /(name: String): File = workingDir / name
}

// TODO predicate on DV for all of them being files?
case class ProcessingContext[
  D <: AnyDataSet,
  DF <: D#Raw
](val dataFiles: DF,
  val workingDir: File
) extends AnyProcessingContext {
  type DataSet = D
  type DataFiles = DF
}


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type IRaw <: Input#Raw

  type Output <: AnyDataSet
  val  output: Output

  // should be provided implicitly:
  val parseInputFiles: AnyApp1At[
    ParseDenotations[FileDataLocation, Input#Keys],
    Map[String, FileDataLocation]
  ] { type Y = Either[
        ParseDenotationsError,
        IRaw
        // DataSetLocations[Input, FileDataLocation]
      ]
    }

  type OutputFiles = DataSetLocations[Output, FileDataLocation]

  // this is where you define what to do
  def process(context: ProcessingContext[Input, IRaw]): Instructions[OutputFiles]


  final def runProcess(workingDir: File, inputFiles: Map[String, File]): Result[Map[String, File]] = {
    parseInputFiles(inputFiles mapValues { f => FileDataLocation(f) }) match {
      case Left(err) => Failure(err.toString)
      case Right(inputFiles) => {
        process(
          ProcessingContext[Input, IRaw](inputFiles, workingDir)
        ).run(workingDir.toJava) match {
          case Failure(tr) => Failure(tr)
          case Success(tr, of) => Success(tr, toMap(of))
        }
      }
    }
  }

}

abstract class DataProcessingBundle[
  I <: AnyDataSet, IR <: I#Raw,
  O <: AnyDataSet
](deps: AnyBundle*)(
  val input: I,
  val output: O
)(implicit
  val parseInputFiles: AnyApp1At[
    ParseDenotations[FileDataLocation, I#Keys],
    Map[String, FileDataLocation]
  ] { type Y = Either[
        ParseDenotationsError,
        IR
        // DataSetLocations[I, FileDataLocation]
      ]
    }
) extends Bundle(deps: _*) with AnyDataProcessingBundle {

  type Input = I
  type Output = O
  type IRaw = IR
}
