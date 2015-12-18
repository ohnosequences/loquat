package ohnosequences.loquat

import ohnosequences.statika.AnyArtifactMetadata
import ohnosequences.statika.aws._

import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.ec2.AnyAmazonLinuxAMI
import ohnosequences.awstools.s3.S3Object

import better.files._


/* Configuration for loquat */
abstract class AnyLoquatConfig extends AnyConfig {
  // TODO: limit allowed symbols and check on validation
  val loquatName: String

  /* Metadata generated for your loquat project */
  val metadata: AnyArtifactMetadata

  /* AMI that will be used for manager and worker instances */
  // NOTE: we need the AMI type here for the manager/worker compats
  type AMI <: AnyAmazonLinuxAMI
  val  ami: AMI

  /* IAM role that will be used by the autoscaling groups */
  val iamRoleName: String

  /* An S3 bucket for saving logs */
  val bucketName: String

  type ManagerConfig <: AnyManagerConfig
  val  managerConfig: ManagerConfig

  type WorkersConfig <: AnyWorkersConfig
  val  workersConfig: WorkersConfig

  /* Termination conditions */
  val terminationConfig: TerminationConfig

  /* List of tiny or big dataMappings */
  val dataMappings: List[AnyDataMapping]



  /* Here follow all the values that are dependent on those defined on top */

  lazy val region: Region = ami.region

  type AMIEnv = amznAMIEnv[AMI]
  lazy val amiEnv: AMIEnv = amznAMIEnv(ami)

  // FIXME: put this constant somewhere else
  final val workingDir: File = file"/media/ephemeral0/applicator/loquat"

  // FIXME: should check that the url string parses to an object address
  lazy final val fatArtifactS3Object: S3Object = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => S3Object(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be published to S3")
    }
  }

  /* Unique id  of the loquat instance */
  lazy final val artifactName: String = metadata.artifact.replace(".", "-").toLowerCase
  lazy final val artifactVersion: String = metadata.version.replace(".", "-").toLowerCase
  lazy final val loquatId: String = s"${loquatName}-${artifactName}-${artifactVersion}"

  lazy final val resourceNames: ResourceNames = ResourceNames(loquatId, bucketName)



  lazy final val subConfigs: List[AnyConfig] = List(
    managerConfig,
    workersConfig
  )

  // TODO: add the artifact check somewhere else
  def validationErrors: Seq[String] = Seq()
  //   val artifactErr =
  //     if (s3.objectExists(fatArtifactS3Object).isSuccess) Seq()
  //     else Seq(s"Couldn't access the artifact at [${fatArtifactS3Object.url}] (probably you forgot to publish it)")
  // }
}
