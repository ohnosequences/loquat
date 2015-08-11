package ohnosequences.nisperito

import ohnosequences.nisperito.tasks._

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.awstools.ec2.{EC2, Tag, InstanceType, InstanceSpecs }
import ohnosequences.awstools.s3.{ S3, ObjectAddress }
import ohnosequences.awstools.autoscaling._

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import org.clapper.avsl.Logger
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
  workingDir: File = new File("/media/ephemeral0"),
  groupSize: WorkersGroupSize = WorkersGroupSize(0, 1, 10)
)

/* Configuration of termination conditions */
case class TerminationConfig(
  // if true nisperito will terminate after solving all initial tasks
  terminateAfterInitialTasks: Boolean,
  // maximum time for processing task
  taskProcessTimeout: Int = 60 * 60 * 10, // 10 hours
  // if true nisperito will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int] = None,
  // if true nisperito will terminate after this timeout reached. Time units are seconds.
  timeout: Option[Int] = None
)

/* Configuration of resources */
case class ResourceNames(nisperitoId: String) {
  // name of queue with tasks
  val inputQueue: String = "nisperitoInputQueue" + nisperitoId
  // name of topic for tasks result notifications
  val outputQueue: String = "nisperitoOutputQueue" + nisperitoId
  // name of queue with tasks results notifications (will be subscribed to outputTopic)
  val outputTopic: String = "nisperitoOutputTopic" + nisperitoId
  // name of topic for errors
  val errorTopic: String = "nisperitoErrorQueue" + nisperitoId
  // name of queue with errors (will be subscribed to errorTopic)
  val errorQueue: String = "nisperitoErrorTopic" + nisperitoId
  // name of bucket for logs and console static files
  val bucket: String = "nisperitobucket" + nisperitoId.replace("_", "-")
}


/* Configuration for nisperito */
abstract class AnyNisperitoConfig {

  // email address for notifications
  val email: String

  // these are credentials that are used to launch nisperito
  val localCredentials: AWSCredentialsProvider

  // Metadata generated for your nisperito project
  val metadata: AnyArtifactMetadata

  // keypair name for connecting to the nisperito instances
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

  // List of tiny or big tasks
  val tasks: List[AnyTask]

  // TODO: AWS region should be also configurable

  /* Here follow all the values that are dependent on those defined on top */

  lazy final val fatArtifactS3Object: ObjectAddress = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => ObjectAddress(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be published to S3")
    }
  }

  // Unique id  of the nisperito instance
  lazy final val nisperitoName: String = metadata.artifact.replace(".", "").toLowerCase
  lazy final val nisperitoVersion: String = metadata.version.replace(".", "").toLowerCase
  lazy final val nisperitoId: String = (nisperitoName + nisperitoVersion)

  lazy final val managerAutoScalingGroup = AutoScalingGroup(
    name = "nisperitoManagerGroup" + nisperitoVersion,
    minSize = 1,
    maxSize = 1,
    desiredCapacity = 1,
    launchingConfiguration = LaunchConfiguration(
      name = "nisperitoManagerLaunchConfiguration" + nisperitoVersion,
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
    name = "nisperitoWorkersGroup" + nisperitoVersion,
    minSize = workersConfig.groupSize.min,
    maxSize = workersConfig.groupSize.max,
    desiredCapacity = workersConfig.groupSize.desired,
    launchingConfiguration = LaunchConfiguration(
      name = "nisperitoWorkersLaunchConfiguration" + nisperitoVersion,
      instanceSpecs = InstanceSpecs(
        instanceType = workersConfig.instanceType,
        amiId = ami.id,
        keyName = keypairName,
        instanceProfile = Some(iamRoleName),
        deviceMapping = Map("/dev/xvdb" -> "ephemeral0")
      ),
      purchaseModel = workersConfig.purchaseModel
    )
  )

  // resources configuration of names for resources: queues, topics, buckets
  lazy final val resourceNames: ResourceNames = ResourceNames(nisperitoId)

  // FIXME: this is just an empty object in S3 witnessing that the initial tasks were uploaded:
  lazy final val tasksUploaded: ObjectAddress = ObjectAddress(resourceNames.bucket, "tasksUploaded")

  lazy final val notificationTopic: String =
    s"""nisperitoNotificationTopic${email.replace("@", "").replace("-", "").replace(".", "")}"""


  // FIXME: fix logging
  val logger = Logger(this.getClass)

  /* This performs some runtime checks of the config */
  def check: Boolean = {

    val ec2 = EC2.create(localCredentials)
    val s3 = S3.create(localCredentials)

    val workers = workersAutoScalingGroup

    if (workers.desiredCapacity < workers.minSize || workers.desiredCapacity > workers.maxSize) {
      logger.error("desired capacity should be in interval [minSize, maxSize]")
      return false
    }

    if(!ec2.isKeyPairExists(keypairName)) {
      logger.error(s"key pair: ${keypairName} doesn't exists")
      return false
    }

    // FIXME: check that fat artifact is published where it is expected
    try {
      logger.info("checking the fat jar location")
      s3.getObjectStream(fatArtifactS3Object) match {
        case null => logger.error("artifact isn't uploaded"); false
        case _ => true
      }
    } catch {
      case s3e: AmazonServiceException if s3e.getStatusCode==301 => true
      case t: Throwable  => {
        logger.error("artifact isn't uploaded: " + fatArtifactS3Object + " " + t)
        false
      }
    }
  }

}
