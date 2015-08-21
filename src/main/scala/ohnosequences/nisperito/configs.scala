package ohnosequences.loquat

import ohnosequences.loquat.dataMappings._

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.awstools.ec2.{EC2, Tag, InstanceType, InstanceSpecs }
import ohnosequences.awstools.s3.{ S3, ObjectAddress }
import ohnosequences.awstools.autoscaling._

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.typesafe.scalalogging.LazyLogging
import java.io.File


/* Manager autoscaling group configuration */
case class ManagerConfig(
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto
)

/* Workers autoscaling group configuration */
case class WorkersGroupSize(min: Int, desired: Int, max: Int)

case class WorkersConfig(
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto,
  groupSize: WorkersGroupSize = WorkersGroupSize(0, 1, 10)
)

/* Configuration of termination conditions */
case class TerminationConfig(
  // if true loquat will terminate after solving all initial dataMappings
  terminateAfterInitialDataMappings: Boolean,
  // maximum time for processing dataMapping
  dataMappingProcessTimeout: Int = 60 * 60 * 10, // 10 hours
  // if true loquat will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int] = None,
  // if true loquat will terminate after this timeout reached. Time units are seconds.
  timeout: Option[Int] = None
)

/* Configuration of resources */
case class ResourceNames(loquatId: String) {
  // name of queue with dataMappings
  val inputQueue: String = "loquatInputQueue" + loquatId
  // name of topic for dataMappings result notifications
  val outputQueue: String = "loquatOutputQueue" + loquatId
  // name of queue with errors (will be subscribed to errorTopic)
  val errorQueue: String = "loquatErrorTopic" + loquatId
  // name of bucket for logs and console static files
  val bucket: String = "era7nisperos"
}


/* Configuration for loquat */
abstract class AnyLoquatConfig extends LazyLogging {

  // email address for notifications
  val email: String

  // these are credentials that are used to launch loquat
  val localCredentials: AWSCredentialsProvider

  // Metadata generated for your loquat project
  val metadata: AnyArtifactMetadata

  // keypair name for connecting to the loquat instances
  val keypairName: String
  val iamRoleName: String

  // AMI that will be used for manager and worker instances
  type AMI <: AmazonLinuxAMI
  val  ami: AMI

  // Configuration for Manager autoscaling group
  val managerConfig: ManagerConfig

  // Configuration for Workers autoscaling group
  val workersConfig: WorkersConfig

  // Termination conditions
  val terminationConfig: TerminationConfig

  // List of tiny or big dataMappings
  val dataMappings: List[AnyDataMapping]

  // TODO: AWS region should be also configurable

  /* Here follow all the values that are dependent on those defined on top */

  // FIXME: put this constant somewhere else
  final val workingDir: File = new File("/media/ephemeral0/applicator/loquat")

  lazy final val fatArtifactS3Object: ObjectAddress = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => ObjectAddress(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be published to S3")
    }
  }

  // Unique id  of the loquat instance
  lazy final val loquatName: String = metadata.artifact.replace(".", "").toLowerCase
  lazy final val loquatVersion: String = metadata.version.replace(".", "").toLowerCase
  lazy final val loquatId: String = (loquatName + loquatVersion)

  lazy final val managerAutoScalingGroup = AutoScalingGroup(
    name = "loquatManagerGroup" + loquatVersion,
    minSize = 1,
    maxSize = 1,
    desiredCapacity = 1,
    launchingConfiguration = LaunchConfiguration(
      name = "loquatManagerLaunchConfiguration" + loquatVersion,
      instanceSpecs = InstanceSpecs(
        instanceType = managerConfig.instanceType,
        amiId = ami.id,
        keyName = keypairName,
        instanceProfile = Some(iamRoleName)
      ),
      purchaseModel = managerConfig.purchaseModel
    )
  )

  lazy final val workersAutoScalingGroup = AutoScalingGroup(
    name = "loquatWorkersGroup" + loquatVersion,
    minSize = workersConfig.groupSize.min,
    maxSize = workersConfig.groupSize.max,
    desiredCapacity = workersConfig.groupSize.desired,
    launchingConfiguration = LaunchConfiguration(
      name = "loquatWorkersLaunchConfiguration" + loquatVersion,
      instanceSpecs = InstanceSpecs(
        instanceType = workersConfig.instanceType,
        amiId = ami.id,
        keyName = keypairName,
        instanceProfile = Some(iamRoleName),
        deviceMapping = Map("/dev/sdb" -> "ephemeral0")
      ),
      purchaseModel = workersConfig.purchaseModel
    )
  )

  // resources configuration of names for resources: queues, topics, buckets
  lazy final val resourceNames: ResourceNames = ResourceNames(loquatId)

  // FIXME: this is just an empty object in S3 witnessing that the initial dataMappings were uploaded:
  lazy final val dataMappingsUploaded: ObjectAddress = ObjectAddress(resourceNames.bucket, loquatId) / "dataMappingsUploaded"

  lazy final val notificationTopic: String =
    s"""loquatNotificationTopic${email.replace("@", "").replace("-", "").replace(".", "")}"""


  /* This performs some runtime checks of the config */
  def check: Boolean = {

    logger.info("checking the config")
    val ec2 = EC2.create(localCredentials)
    val s3 = S3.create(localCredentials)

    val workers = workersAutoScalingGroup

    if (workers.desiredCapacity < workers.minSize || workers.desiredCapacity > workers.maxSize) {
      logger.error(s"desired capacity [${workers.desiredCapacity}] should be in the interval [${workers.minSize}, ${workers.maxSize}]")
      return false
    }

    if(!ec2.isKeyPairExists(keypairName)) {
      logger.error(s"key pair: ${keypairName} doesn't exists")
      return false
    }

    // logger.debug(s"checking the fat jar location: ${fatArtifactS3Object.url}")
    if (s3.objectExists(fatArtifactS3Object).isFailure) {
      logger.error("Couldn't access the fat artifact (probably you forgot to publish it)")
      return false
    }

    true
  }

}
