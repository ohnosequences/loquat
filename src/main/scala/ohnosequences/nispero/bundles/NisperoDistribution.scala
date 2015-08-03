package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._
import ohnosequences.nispero.bundles.console.Console


trait NisperoDistributionAux extends AnyBundle {
  type Manager <: AnyManager
  val  manager: Manager

  val  console: Console

  val  metadata: AnyArtifactMetadata = manager.metadata

  type AMI = manager.resourcesBundle.aws.AMI
  val  ami = manager.resourcesBundle.aws.ami

  val bundleDependencies: List[AnyBundle] = List(manager, console)

  case object managerCompat extends Compatible(ami, manager, metadata)
  case object consoleCompat extends Compatible(ami, console, metadata)

  def install: Results = {
    success("nispero distribution installed")
  }
}

abstract class NisperoDistribution[M <: AnyManager]
  (val manager: M, val console: Console) extends NisperoDistributionAux {

  type Manager = M
}
