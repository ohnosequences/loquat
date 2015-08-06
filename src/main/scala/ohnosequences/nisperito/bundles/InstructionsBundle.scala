package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import java.io.File


trait AnyInstructionsBundle extends AnyBundle {

  /* this is where user describes instructions how to process each task:
     - it gets the list of input files
     - it must produce output files declared in the task
  */
  def processTask(inputFiles: Map[String, File], outputFiles: Map[String, File]): Results
}

abstract class InstructionsBundle(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyInstructionsBundle
