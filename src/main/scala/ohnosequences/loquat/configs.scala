package ohnosequences.loquat

import dataMappings._, utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.awstools.ec2.{EC2, Tag, InstanceType, InstanceSpecs }
import ohnosequences.awstools.s3._
import ohnosequences.awstools.autoscaling._

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import java.io.File
import scala.util.Try


case object configs {

  /* Any config here can validate itself (in runtime) */
  trait AnyConfig extends LazyLogging {

    lazy val label: String = this.toString

    def validationErrors: Seq[String]

    /* Config dependencies */
    val subConfigs: Seq[AnyConfig]

    /* This method validates subconfigs and logs validation errors */
    final def validate: Seq[String] = {
      val subErrors = subConfigs.flatMap{ _.validate }

      val errors = validationErrors
      errors.foreach{ msg => logger.error(msg) }

      if (errors.isEmpty) logger.debug(s"Validated [${label}] config")

      subErrors ++ errors
    }
  }

  abstract class Config(val subConfigs: AnyConfig*) extends AnyConfig


  def checkPurchaseModel(pm: PurchaseModel): Seq[String] = pm match {
    case Spot(price) if price <= 0 => Seq(s"Spot price must be positive: ${price}")
    case _ => Seq()
  }


  /* Manager autoscaling group configuration */
  case class ManagerConfig(
    instanceType: InstanceType,
    purchaseModel: PurchaseModel
  ) extends Config() {

    def validationErrors: Seq[String] = checkPurchaseModel(purchaseModel)
  }

  /* Workers autoscaling group configuration */
  case class WorkersGroupSize(
    min: Int,
    desired: Int,
    max: Int
  ) extends Config() {

    def validationErrors: Seq[String] =
      if (
        desired < min ||
        desired > max
      ) Seq(s"Desired capacity [${desired}] has to be in the interval [${min}, ${max}]")
      else Seq()
  }


  case class WorkersConfig(
    instanceType: InstanceType,
    purchaseModel: PurchaseModel,
    groupSize: WorkersGroupSize,
    // TODO: use some better type for this
    deviceMapping: Map[String, String] = Map("/dev/sdb" -> "ephemeral0")
  ) extends Config(groupSize) {

    def validationErrors: Seq[String] = {
      checkPurchaseModel(purchaseModel) ++
      groupSize.validationErrors
    }
  }

  /* Configuration of termination conditions */
  case class TerminationConfig(
    // if true loquat will terminate after solving all initial tasks
    terminateAfterInitialDataMappings: Boolean,
    // if true loquat will terminate after errorQueue will contain more unique messages then threshold
    errorsThreshold: Option[Int] = None,
    // maximum time for processing one task
    taskProcessingTimeout: Option[Time] = None,
    // maximum time for everything
    globalTimeout: Option[Time] = None
  ) extends Config() {

    def validationErrors: Seq[String] = {
      val treshholdErr = errorsThreshold match {
        case Some(n) if n <= 0 => Seq(s"Errors threshold has to be positive: ${n}")
        case _ => Seq()
      }

      val localTimeoutErr = taskProcessingTimeout match {
        case Some(time) if (
            time.inSeconds < 0 ||
            time.inSeconds > Hours(12).inSeconds
          ) => Seq(s"Task processing timeout [${time}] has to be between 0 seconds and 12 hours")
        case _ => Seq()
      }

      val globalTimeoutErr = globalTimeout match {
        case Some(time) if (
            time.inSeconds < 0 ||
            time.inSeconds > Hours(12).inSeconds
          ) => Seq(s"Global timeout [${time}] has to be between 0 seconds and 12 hours")
        case _ => Seq()
      }

      treshholdErr ++ localTimeoutErr ++ globalTimeoutErr
    }
  }

  /* Configuration of resources */
  protected[loquat]
    case class ResourceNames(suffix: String) {
      /* name of queue with dataMappings */
      val inputQueue: String = "loquatInputQueue" + suffix
      /* name of topic for dataMappings result notifications */
      val outputQueue: String = "loquatOutputQueue" + suffix
      /* name of queue with errors (will be subscribed to errorTopic) */
      val errorQueue: String = "loquatErrorTopic" + suffix
      /* name of bucket for logs files */
      val bucket: String = "era7loquats"
      /* topic name to notificate user about termination of loquat */
      val notificationTopic: String = "loquatNotificationTopic" + suffix
      /* name of the manager autoscaling group */
      val managerGroup: String = "loquatManagerGroup" + suffix
      /* name of the workers autoscaling group */
      val workersGroup: String = "loquatWorkersGroup" + suffix
    }


  /* Simple type to separate user-related data from the config */
  case class LoquatUser(
    /* email address for notifications */
    val email: String,
    /* these are credentials that are used to launch loquat */
    val localCredentials: AWSCredentialsProvider,
    /* keypair name for connecting to the loquat instances */
    val keypairName: String
  ) extends Config() {

    def   deploy[L <: AnyLoquat](l: L): Unit = l.deploy(this)
    def undeploy[L <: AnyLoquat](l: L): Unit = l.undeploy(this)

    def validationErrors: Seq[String] = {
      val emailErr =
        if (email.contains('@')) Seq()
        else Seq(s"User email [${email}] has invalid format")

      emailErr ++ {
        if (Try( localCredentials.getCredentials ).isFailure)
          Seq(s"Couldn't load your local credentials: ${localCredentials}")
          // TODO: add account permissions validation
        else {
          val ec2 = EC2.create(localCredentials)
          if(ec2.isKeyPairExists(keypairName)) Seq()
          else Seq(s"key pair: ${keypairName} doesn't exists")
        }
      }
    }
  }


  /* Configuration for loquat */
  abstract class AnyLoquatConfig extends AnyConfig {

    /* Metadata generated for your loquat project */
    val metadata: AnyArtifactMetadata

    /* AMI that will be used for manager and worker instances */
    // NOTE: we need the AMI type here for the manager/worker compats
    type AMI <: AmazonLinuxAMI
    val  ami: AMI

    /* IAM rolse that will be used by the autoscaling groups */
    val iamRoleName: String

    /* Configuration for Manager autoscaling group */
    val managerConfig: ManagerConfig

    /* Configuration for Workers autoscaling group */
    val workersConfig: WorkersConfig

    /* Termination conditions */
    val terminationConfig: TerminationConfig

    /* List of tiny or big dataMappings */
    val dataMappings: List[AnyDataMapping]

    // TODO: AWS region should be also configurable


    /* Here follow all the values that are dependent on those defined on top */

    // FIXME: put this constant somewhere else
    final val workingDir: File = new File("/media/ephemeral0/applicator/loquat")

    // FIXME: should check that the url string parses to an object address
    lazy final val fatArtifactS3Object: S3Object = {
      val s3url = """s3://(.+)/(.+)""".r
      metadata.artifactUrl match {
        case s3url(bucket, key) => S3Object(bucket, key)
        case _ => throw new Error("Wrong fat jar url, it should be published to S3")
      }
    }

    /* Unique id  of the loquat instance */
    lazy final val loquatName: String = metadata.artifact.replace(".", "").toLowerCase
    lazy final val loquatVersion: String = metadata.version.replace(".", "").toLowerCase
    lazy final val loquatId: String = (loquatName + loquatVersion)

    lazy final val resourceNames: ResourceNames = ResourceNames(loquatId)

    def managerAutoScalingGroup(keypairName: String): AutoScalingGroup =
      AutoScalingGroup(
        name = resourceNames.managerGroup,
        minSize = 1,
        maxSize = 1,
        desiredCapacity = 1,
        launchingConfiguration = LaunchConfiguration(
          name = "loquatManagerLaunchConfiguration" + loquatId,
          instanceSpecs = InstanceSpecs(
            instanceType = managerConfig.instanceType,
            amiId = ami.id,
            keyName = keypairName,
            instanceProfile = Some(iamRoleName)
          ),
          purchaseModel = managerConfig.purchaseModel
        )
      )

    def workersAutoScalingGroup(keypairName: String): AutoScalingGroup =
      AutoScalingGroup(
        name = resourceNames.workersGroup,
        minSize = workersConfig.groupSize.min,
        maxSize = workersConfig.groupSize.max,
        desiredCapacity = workersConfig.groupSize.desired,
        launchingConfiguration = LaunchConfiguration(
          name = "loquatWorkersLaunchConfiguration" + loquatId,
          instanceSpecs = InstanceSpecs(
            instanceType = workersConfig.instanceType,
            amiId = ami.id,
            keyName = keypairName,
            instanceProfile = Some(iamRoleName),
            deviceMapping = workersConfig.deviceMapping
          ),
          purchaseModel = workersConfig.purchaseModel
        )
      )

    // FIXME: this is just an empty object in S3 witnessing that the initial dataMappings were uploaded:
    lazy final val dataMappingsUploaded: S3Object = S3Object(resourceNames.bucket, loquatId) / "dataMappingsUploaded"


    lazy final val subConfigs: List[AnyConfig] = List(
      managerConfig,
      workersConfig
    )

    // TODO: add the artifact check somewhere else
    def validationErrors: Seq[String] = Seq()
    //   val s3  = S3.create(creds)
    //   val artifactErr =
    //     if (s3.objectExists(fatArtifactS3Object).isSuccess) Seq()
    //     else Seq(s"Couldn't access the artifact at [${fatArtifactS3Object.url}] (probably you forgot to publish it)")
    // }
  }

}
