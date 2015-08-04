package ohnosequences.nispero.bundles

import ohnosequences.nispero._
import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.awstools.s3.S3
import java.io.File

import org.clapper.avsl.Logger
import ohnosequences.awstools.s3.LoadingManager
import org.apache.commons.io.FileUtils
import ohnosequences.nispero.utils.Utils


trait AnyInstructionsBundle extends AnyBundle {

  /* this is where user describes instructions how to process each task:
     - it gets the list of input files
     - it must produce output files declared in the task
  */
  def processTask(task: AnyTask, inputFiles: List[File], outputDir: File): Results
}

abstract class InstructionsBundle(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyInstructionsBundle
