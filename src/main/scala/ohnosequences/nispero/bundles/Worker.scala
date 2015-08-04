package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.nispero.worker.InstructionsExecutor


trait AnyWorker extends AnyBundle {

  type Instructions <: AnyInstructions
  val  instructions: Instructions

  val resourcesBundle: ResourcesBundle
  val logUploader: LogUploader

  val bundleDependencies: List[AnyBundle] = List(instructions, resourcesBundle, logUploader)

  def install: Results = {
    val config = resourcesBundle.config
    val instructionsExecutor = new InstructionsExecutor(config, instructions.instructions, resourcesBundle.awsClients)
    instructionsExecutor.run
    success("worker installed")
  }
}

abstract class Worker[I <: AnyInstructions](
  val instructions: I,
  val resourcesBundle: ResourcesBundle,
  val logUploader: LogUploader
) extends AnyWorker {

  type Instructions = I
}
