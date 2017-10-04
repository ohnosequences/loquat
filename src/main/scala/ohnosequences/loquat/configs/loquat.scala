package ohnosequences.loquat

import ohnosequences.statika.AnyArtifactMetadata
import ohnosequences.statika.aws._
import ohnosequences.awstools.regions._
import ohnosequences.awstools.ec2.AnyAmazonLinuxAMI
import ohnosequences.awstools.s3._
import scala.concurrent.duration._
import java.net.URI


/* Configuration for loquat */
abstract class AnyLoquatConfig extends AnyConfig {
  // TODO: limit allowed symbols and check on validation
  val loquatName: String

  /* IAM role that will be used by the autoscaling groups */
  val iamRoleName: String

  /* An S3 folder for saving instance logs */
  val logsS3Prefix: S3Folder

  /* Metadata generated for your loquat project */
  val metadata: AnyArtifactMetadata

  val managerConfig: AnyManagerConfig
  val workersConfig: AnyWorkersConfig

  /* This settings can be changed to alter termination conditions */
  val terminationConfig: TerminationConfig = TerminationConfig.default()

  /* This setting switches the check of existence of the input S3 objects */
  val checkInputObjects: Boolean = true

  /* This setting determines whether empty output files will be uploaded or not */
  val skipEmptyResults: Boolean = true

  /* This setting defines the default SQS messages timeout that will be set on deploy */
  val sqsInitialTimeout: FiniteDuration = 30.minutes


  /* Here follow all the values that are dependent on those defined on top */
  lazy val configLabel: String = s"${loquatName} config"

  lazy val ami: AnyAmazonLinuxAMI = managerConfig.ami
  lazy val amiEnv: AnyLinuxAMIEnvironment = amznAMIEnv(ami)
  lazy val region: Region = ami.region

  lazy final val fatArtifactS3Object: S3Object = S3Object(new URI(metadata.artifactUrl))

  /* Unique id  of the loquat instance */
  lazy final val artifactName: String = metadata.artifact.replace(".", "-").toLowerCase
  lazy final val artifactVersion: String = metadata.version.replace(".", "-").toLowerCase
  lazy final val loquatId: String = s"${loquatName}-${artifactName}-${artifactVersion}"

  lazy final val resourceNames: ResourceNames = ResourceNames(loquatId, logsS3Prefix)



  lazy final val subConfigs: List[AnyConfig] = List(
    managerConfig,
    workersConfig
  )

  def validationErrors(aws: AWSClients): Seq[String] = {
    logger.info("Checking the fat-artifact existence...")
    if (! aws.s3.objectExists(fatArtifactS3Object)) {
      Seq(s"Couldn't access the artifact at [${fatArtifactS3Object}] (probably you forgot to publish it)")
    } else Seq()
  }

}
