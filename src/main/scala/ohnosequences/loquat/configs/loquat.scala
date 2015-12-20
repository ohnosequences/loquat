package ohnosequences.loquat

import utils._

import ohnosequences.statika.AnyArtifactMetadata
import ohnosequences.statika.aws._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.ec2.AnyAmazonLinuxAMI
import ohnosequences.awstools.s3._

import ohnosequences.datasets._

import better.files._


/* Configuration for loquat */
abstract class AnyLoquatConfig extends AnyConfig {
  // TODO: limit allowed symbols and check on validation
  val loquatName: String

  /* IAM role that will be used by the autoscaling groups */
  val iamRoleName: String

  /* An S3 bucket for saving logs */
  val logsBucketName: String

  /* Metadata generated for your loquat project */
  val metadata: AnyArtifactMetadata

  val managerConfig: AnyManagerConfig
  val workersConfig: AnyWorkersConfig

  val terminationConfig: TerminationConfig

  val dataMappings: List[AnyDataMapping]



  /* Here follow all the values that are dependent on those defined on top */
  lazy val configLabel: String = s"${loquatName} config"

  lazy val ami: AnyAmazonLinuxAMI = managerConfig.instanceSpecs.ami
  lazy val amiEnv: AnyLinuxAMIEnvironment = amznAMIEnv(ami)
  lazy val region: Region = ami.region

  final val workingDir: File = file"/media/ephemeral0/applicator/loquat"

  lazy final val fatArtifactS3Object: S3Object = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => S3Object(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be an S3 address")
    }
  }

  /* Unique id  of the loquat instance */
  lazy final val artifactName: String = metadata.artifact.replace(".", "-").toLowerCase
  lazy final val artifactVersion: String = metadata.version.replace(".", "-").toLowerCase
  lazy final val loquatId: String = s"${loquatName}-${artifactName}-${artifactVersion}"

  lazy final val resourceNames: ResourceNames = ResourceNames(loquatId, logsBucketName)



  lazy final val subConfigs: List[AnyConfig] = List(
    managerConfig,
    workersConfig
  )

  def validationErrors(aws: AWSClients): Seq[String] = {

    if (aws.s3.objectExists(fatArtifactS3Object).isFailure)
      Seq(s"Couldn't access the artifact at [${fatArtifactS3Object.url}] (probably you forgot to publish it)")
    else {

      val allInputs: List[Map[String, AnyS3Address]] = dataMappings map { dataMapping =>

        val inputs: Map[String, AnyS3Address] =
          toMap(dataMapping.remoteInput).foldLeft(Map[String, AnyS3Address]()) { (acc, x) =>
            x match {
              case (key, S3Resource(address)) => acc + (key -> address)
              case _ => acc
            }
          }

        inputs
      }

      import scala.util.Try

      val inputsCheck: Seq[String] = allInputs flatMap { inputs =>

        inputs flatMap { case (key, address) =>

          val exists: Boolean = Try {
            aws.s3.s3.getObjectMetadata(address.bucket, address.key)
          }.isSuccess

          if (exists) None
          else Some(
            s"Input object [${key}] does not exists at the address: [${address.url}]"
          )

        }
      }

      inputsCheck
    }

  }

}
