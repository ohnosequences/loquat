package ohnosequences.nispero

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.awstools.ec2.{EC2, Tag, InstanceType, InstanceSpecs }
import ohnosequences.awstools.s3.{ S3, ObjectAddress }
import ohnosequences.awstools.autoscaling._

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import java.io.File
import org.clapper.avsl.Logger


/* Manager autoscaling group configuration */
case class ManagerConfig(
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto
)

/* Workers autoscaling group configuration */
case class WorkersConfig(
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto,
  workingDir: File = new File("/media/ephemeral0"),
  desiredCapacity: Int = 1,
  minSize: Int = 0,
  maxSize: Int = 10
)

/* Configuration of termination conditions */
case class TerminationConfig(
  // maximum time for processing task
  taskProcessTimeout: Int = 60 * 60 * 10, // 10 hours
  // if true nispero will terminate after solving all initial tasks
  terminateAfterInitialTasks: Boolean,
  // if true nispero will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int] = None,
  // if true nispero will terminate after this timeout reached. Time units are sceonds.
  timeout: Option[Int] = None
)

/* Configuration of resources */
case class ResourceNames(nisperoId: String) {
  // name of queue with tasks
  val inputQueue: String = "nisperoInputQueue" + nisperoId
  // name of queue for interaction with Manager
  val controlQueue: String = "nisperoControlQueue" + nisperoId
  // name of topic for tasks result notifications
  val outputQueue: String = "nisperoOutputQueue" + nisperoId
  // name of queue with tasks results notifications (will be subscribed to outputTopic)
  val outputTopic: String = "nisperoOutputTopic" + nisperoId
  // name of topic for errors
  val errorTopic: String = "nisperoErrorQueue" + nisperoId
  // name of queue with errors (will be subscribed to errorTopic)
  val errorQueue: String = "nisperoErrorTopic" + nisperoId
  // name of bucket for logs and console static files
  val bucket: String = "nisperobucket" + nisperoId.replace("_", "-")
}


/* Configuration for nispero */
abstract class AnyNisperoConfig {

  // email address for notifications
  val email: String

  // these are credentials that are used to launch nispero
  val localCredentials: AWSCredentialsProvider

  // Metadata generated for your nispero project
  val metadata: AnyArtifactMetadata

  lazy final val fatArtifactS3Object: ObjectAddress = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => ObjectAddress(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be published to S3")
    }
  }

  // Unique id  of the nispero instance
  lazy final val nisperoName: String = metadata.artifact.replace(".", "").toLowerCase
  lazy final val nisperoVersion: String = metadata.version.replace(".", "").toLowerCase
  lazy final val nisperoId: String = (nisperoName + nisperoVersion)

  // keypair name for connecting to the nispero instances
  val keypairName: String
  val securityGroups: List[String]
  val iamRoleName: String

  type AMI <: AmazonLinuxAMI
  val  ami: AMI

  // Configuration for Manager autoscaling group
  val managerConfig: ManagerConfig

  lazy final val managerAutoScalingGroup = AutoScalingGroup(
    name = "nisperoManagerGroup" + nisperoVersion,
    minSize = 1,
    maxSize = 1,
    desiredCapacity = 1,
    launchingConfiguration = LaunchConfiguration(
      name = "nisperoManagerLaunchConfiguration" + nisperoVersion,
      instanceSpecs = InstanceSpecs(
        instanceType = managerConfig.instanceType,
        amiId = ami.id,
        securityGroups = securityGroups,
        keyName = keypairName,
        instanceProfile = Some(iamRoleName)
      ),
      purchaseModel = managerConfig.purchaseModel
    )
  )

  // Configuration for Workers autoscaling group
  val workersConfig: WorkersConfig

  lazy final val workersAutoScalingGroup = AutoScalingGroup(
    name = "nisperoWorkersGroup" + nisperoVersion,
    minSize = workersConfig.minSize,
    maxSize = workersConfig.maxSize,
    desiredCapacity = workersConfig.desiredCapacity,
    launchingConfiguration = LaunchConfiguration(
      name = "nisperoWorkersLaunchConfiguration" + nisperoVersion,
      instanceSpecs = InstanceSpecs(
        instanceType = workersConfig.instanceType,
        amiId = ami.id,
        securityGroups = securityGroups,
        keyName = keypairName,
        instanceProfile = Some(iamRoleName),
        deviceMapping = Map("/dev/xvdb" -> "ephemeral0")
      ),
      purchaseModel = workersConfig.purchaseModel
    )
  )

  // Termination conditions
  val terminationConfig: TerminationConfig

  // task provider (@see https://github.com/ohnosequences/nispero/blob/master/doc/tasks-providers.md)
  val tasks: List[AnyTask]

  // resources configuration of names for resources: queues, topics, buckets
  lazy final val resourceNames: ResourceNames = ResourceNames(nisperoId)

  lazy final val initialTasks: ObjectAddress = ObjectAddress(resourceNames.bucket, "initialTasks")
  lazy final val tasksUploaded: ObjectAddress = ObjectAddress(resourceNames.bucket, "tasksUploaded")

  lazy final val notificationTopic: String =
    s"""nisperoNotificationTopic${email.replace("@", "").replace("-", "").replace(".", "")}"""


  def check(): Boolean = {
    val logger = Logger(this.getClass)

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

/* This is a constructor for the end-user, exposing only missing parameters */
case class NisperoConfig[A <: AmazonLinuxAMI](
  val email: String,
  val localCredentials: AWSCredentialsProvider,
  val metadata: AnyArtifactMetadata,
  val keypairName: String,
  val securityGroups: List[String],
  val iamRoleName: String,
  val ami: A,
  val managerConfig: ManagerConfig,
  val workersConfig: WorkersConfig,
  val terminationConfig: TerminationConfig,
  val tasks: List[AnyTask]
) extends AnyNisperoConfig { type AMI = A }
