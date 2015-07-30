package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._, amazonLinuxAMIs._
import ohnosequences.nispero.Config
import ohnosequences.awstools.s3.ObjectAddress

abstract class Configuration extends Bundle() {

  val metadata: AnyArtifactMetadata

  type AMI <: AmazonLinuxAMI
  val  ami: AMI

  val config: Config

  def generateId(metadata: AnyArtifactMetadata): String = {
    val name = metadata.artifact.replace(".", "")
    val version = metadata.version.replace(".", "")
    (name + version).toLowerCase
  }

  def getAddress(url: String): ObjectAddress = {
    val s3url = """s3://(.+)/(.+)""".r
    url match {
      case s3url(bucket, key) => ObjectAddress(bucket, key)
      case _ => throw new Error("wrong fat jar url, check your publish settings")
    }
  }

  def install: Results = {
    success("configuration installed")
  }
}
