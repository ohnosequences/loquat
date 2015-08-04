package ohnosequences.nispero

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.ec2.{ InstanceType, InstanceSpecs }
import ohnosequences.awstools.autoscaling._

/* Manager autoscaling group configuration */
case class ManagerConfig(
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto
)

/* Workers autoscaling group configuration */
case class WorkersConfig(
  workingDir: String,
  desiredCapacity: Int = 1,
  minSize: Int = 0,
  maxSize: Int = 10,
  instanceType: InstanceType,
  purchaseModel: PurchaseModel = SpotAuto
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
abstract class AnyConfig {

  // email address for notifications
  val email: String

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
        instanceProfile = Some(iamRoleName)
      ),
      purchaseModel = workersConfig.purchaseModel
    )
  )

  // Termination conditions
  val terminationConfig: TerminationConfig

  // task provider (@see https://github.com/ohnosequences/nispero/blob/master/doc/tasks-providers.md)
  val tasksProvider: TasksProvider

  // resources configuration of names for resources: queues, topics, buckets
  lazy final val resourceNames: ResourceNames = ResourceNames(nisperoId)

  lazy final val initialTasks: ObjectAddress = ObjectAddress(resourceNames.bucket, "initialTasks")
  lazy final val tasksUploaded: ObjectAddress = ObjectAddress(resourceNames.bucket, "tasksUploaded")

  lazy final val notificationTopic: String = s"""nisperoNotificationTopic${email.replace("@", "").replace("-", "").replace(".", "")}"""

}
