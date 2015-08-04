package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.nispero.worker.InstructionsExecutor


trait AnyWorkerBundle extends AnyBundle {

  type InstructionsBundle <: AnyInstructionsBundle
  val  instructionsBundle: InstructionsBundle

  type ResourcesBundle <: AnyResourcesBundle
  val  resources: ResourcesBundle

  val logUploader: LogUploaderBundle

  val bundleDependencies: List[AnyBundle] = List(instructionsBundle, resources, logUploader)

  def install: Results = {
    val config = resources.config
    val instructionsExecutor = new InstructionsExecutor(config, instructionsBundle, resources.aws)
    instructionsExecutor.run
    success("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  R <: AnyResourcesBundle
](val instructionsBundle: I,
  val resources: R,
  val logUploader: LogUploaderBundle
) extends AnyWorkerBundle {

  type InstructionsBundle = I
  type ResourcesBundle = R
}
