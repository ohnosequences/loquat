package ohnosequences.loquat

import utils._, files._
import ohnosequences.datasets._
import ohnosequences.cosas._, records._
import ohnosequences.statika._

trait AnyProcessingContext {

  val workingDir: File
  val inputFiles: Map[String, File]

  type DataSet <: AnyDataSet

  /* user can get the file corresponding to the given data key */
  def inputFile[K <: AnyData](key: K)(implicit
    isIn: DataSet hasKey K
  ): File = inputFiles(key.label)

  /* or create a file instance in the working directory */
  def /(name: String): File = workingDir / name
}

case class ProcessingContext[D <: AnyDataSet](
  val workingDir: File,
  val inputFiles: Map[String, File]
) extends AnyProcessingContext { type DataSet = D }


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type OutputFiles = ResourcesSet[Output, FileResource]

  // this is where you define what to do
  def process(context: ProcessingContext[Input]): AnyInstructions.withBound[OutputFiles]


  final def runProcess(workingDir: File, inputFiles: Map[String, File]): Result[Map[String, File]] = {
    process(ProcessingContext[Input](workingDir, inputFiles))
      .run(workingDir) match {
        case Failure(tr) => Failure(tr)
        case Success(tr, of) => Success(tr,
          of.asList.map { d => (d.tpe.label, d.value.resource) }.toMap
        )
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
