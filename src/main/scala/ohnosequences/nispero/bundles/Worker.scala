package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.nispero.worker.InstructionsExecutor


abstract class Worker[I <: AnyInstructions](
  val instructions: I,
  val resourcesBundle: ResourcesBundle,
  val logUploader: LogUploader,
  val aws: AWSBundle
) extends AnyWorker {

  type Instructions = I
}

trait AnyWorker extends AnyBundle {

  type Instructions <: AnyInstructions
  val  instructions: Instructions

  val resourcesBundle: ResourcesBundle
  val aws: AWSBundle
  val logUploader: LogUploader

  val bundleDependencies: List[AnyBundle] = List(instructions, resourcesBundle, logUploader, aws)

  def install: Results = {
    val config = resourcesBundle.aws.config
    val instructionsExecutor = new InstructionsExecutor(config, instructions.instructions, aws.clients)
    instructionsExecutor.run
    success("worker installed")
  }
}
