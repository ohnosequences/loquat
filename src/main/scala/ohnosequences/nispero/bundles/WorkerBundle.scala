package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.nispero.worker.InstructionsExecutor


trait AnyWorkerBundle extends AnyBundle {

  type InstructionsBundle <: AnyInstructionsBundle
  val  instructionsBundle: InstructionsBundle

  type ResourcesBundle <: AnyResourcesBundle
  val  resourcesBundle: ResourcesBundle

  val logUploaderBundle: LogUploaderBundle

  val bundleDependencies: List[AnyBundle] = List(instructionsBundle, resourcesBundle, logUploaderBundle)

  def install: Results = {
    val config = resourcesBundle.config
    val instructionsExecutor = new InstructionsExecutor(config, instructionsBundle, resourcesBundle.awsClients)
    instructionsExecutor.run
    success("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  R <: AnyResourcesBundle
](val instructionsBundle: I,
  val resourcesBundle: R,
  val logUploaderBundle: LogUploaderBundle
) extends AnyWorkerBundle {

  type InstructionsBundle = I
  type ResourcesBundle = R
}
