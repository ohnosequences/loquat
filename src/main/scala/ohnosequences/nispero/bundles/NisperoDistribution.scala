package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._


trait AnyNisperoDistribution extends AnyBundle {
  type Manager <: AnyManager
  val  manager: Manager

  val  metadata: AnyArtifactMetadata = manager.metadata

  type AMI = manager.resourcesBundle.aws.AMI
  val  ami = manager.resourcesBundle.aws.ami

  val bundleDependencies: List[AnyBundle] = List(manager)

  case object managerCompat extends Compatible(ami, manager, metadata)

  def install: Results = {
    success("nispero distribution installed")
  }
}

abstract class NisperoDistribution[M <: AnyManager]
  (val manager: M) extends AnyNisperoDistribution {

  type Manager = M
}
