package ohnosequences.nispero.bundles

import ohnosequences.nispero._
import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.awstools.s3.S3
import java.io.File


trait AnyInstructionsBundle extends AnyBundle {

  def execute(s3: S3, task: AnyTask, workingDir: File = new File(".")): TaskResult
}

abstract class InstructionsBundle(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyInstructionsBundle
