package ohnosequences.loquat

import dataMappings._, utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._

import ohnosequences.awstools.ec2._
import ohnosequences.awstools.s3._
import ohnosequences.awstools.autoscaling._

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import better.files._
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



  trait AnyAutoScalingConfig extends Config() { conf =>

    type InstanceSpecs <: AnyInstanceSpecs
    val  instanceSpecs: InstanceSpecs

    type PurchaseModel <: AnyPurchaseModel
    val  purchaseModel: PurchaseModel

    val groupSize: AutoScalingGroupSize

    // TODO: use some better type for this
    val deviceMapping: Map[String, String]

    def validationErrors: Seq[String] = {

      val groupSizeErros: Seq[String] = {
        if ( groupSize.min < 0 ) Seq(s"Minimal autoscaling group size has to be non-negative: ${groupSize.min}")
        else if (
          groupSize.desired < groupSize.min ||
          groupSize.desired > groupSize.max
        ) Seq(s"Desired capacity [${groupSize.desired}] has to be in the interval [${groupSize.min}, ${groupSize.max}]")
        else Seq()
      }

      val purchaseModelErrors: Seq[String] = purchaseModel match {
        case Spot(price) if price <= 0 => Seq(s"Spot price has to be positive: ${price}")
        case _ => Seq()
      }

      groupSizeErros ++ purchaseModelErrors
    }

    def autoScalingGroup(
      groupName: String,
      keypairName: String,
      iamRoleName: String
    ): AutoScalingGroup =
      AutoScalingGroup(
        name = groupName,
        size = conf.groupSize,
        launchConfiguration = LaunchConfiguration(
          name = groupName + "loquatWorkersLaunchConfiguration",
          purchaseModel = conf.purchaseModel,
          launchSpecs = LaunchSpecs(
            conf.instanceSpecs
          )(keyName = keypairName,
            instanceProfile = Some(iamRoleName),
            deviceMapping = conf.deviceMapping
          )
        )
      )

  }

  /* Manager autoscaling group configuration */
  trait AnyManagerConfig extends AnyAutoScalingConfig {

    val groupSize = AutoScalingGroupSize(1, 1, 1)
    val deviceMapping = Map[String, String]()
  }

  case class ManagerConfig[
    IS <: AnyInstanceSpecs,
    PM <: AnyPurchaseModel
  ](instanceSpecs: IS,
    purchaseModel: PM
  ) extends AnyManagerConfig {

    type InstanceSpecs = IS
    type PurchaseModel = PM
  }

  /* Workers autoscaling group configuration */

  trait AnyWorkersConfig extends AnyAutoScalingConfig

  case class WorkersConfig[
    IS <: AnyInstanceSpecs,
    PM <: AnyPurchaseModel
  ](instanceSpecs: IS,
    purchaseModel: PM,
    groupSize: AutoScalingGroupSize,
    // TODO: use some better type for this
    deviceMapping: Map[String, String] = Map("/dev/sdb" -> "ephemeral0")
  ) extends AnyWorkersConfig {

    type InstanceSpecs = IS
    type PurchaseModel = PM
  }


  trait AnyTerminationReason {
    def check: Boolean
    def msg: String
  }

  case class TerminateAfterInitialDataMappings(
    val isOn: Boolean,
    val initialCount: Int,
    val successfulCount: Int
  ) extends AnyTerminationReason {

    def check: Boolean = isOn && (successfulCount >= initialCount)

    def msg: String = s"""|Terminated after successfully processing all the initial data mappings.
      |  Initial data mappings count: ${initialCount}
      |  Successful results: ${successfulCount}
      |""".stripMargin
  }

  case class TerminateWithTooManyErrors(
    val errorsThreshold: Option[Int],
    val failedCount: Int
  ) extends AnyTerminationReason {

    def check: Boolean = errorsThreshold.map{ failedCount >= _ }.getOrElse(false)

    def msg: String = s"""|Terminated due to too many errors.
      |  Errors threshold: ${errorsThreshold}
      |  Failed results count: ${failedCount}
      |""".stripMargin
  }

  case class TerminateAfterGlobalTimeout(
    val globalTimeout: Option[Time],
    val startTime: Option[Time]
  ) extends AnyTerminationReason {

    def check: Boolean = (startTime, globalTimeout) match {
      case (Some(timestamp), Some(globalTimeout)) =>
        (System.currentTimeMillis - timestamp.inSeconds) > globalTimeout.inSeconds
      case _ => false
    }

    def msg: String = s"Terminated due to the global timeout: ${globalTimeout.getOrElse(Seconds(0))}"
  }

  case object TerminateManually extends AnyTerminationReason {
    def check: Boolean = true
    def msg: String = "Terminated manually"
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
    type AMIEnv <: AnyLinuxAMIEnvironment
    val  amiEnv: AMIEnv

    /* IAM rolse that will be used by the autoscaling groups */
    val iamRoleName: String

    type ManagerConfig <: AnyManagerConfig
    val  managerConfig: ManagerConfig

    type WorkersConfig <: AnyWorkersConfig
    val  workersConfig: WorkersConfig

    /* Termination conditions */
    val terminationConfig: TerminationConfig

    /* List of tiny or big dataMappings */
    val dataMappings: List[AnyDataMapping]

    // TODO: AWS region should be also configurable


    /* Here follow all the values that are dependent on those defined on top */

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
    lazy final val loquatName: String = metadata.artifact.replace(".", "").toLowerCase
    lazy final val loquatVersion: String = metadata.version.replace(".", "").toLowerCase
    lazy final val loquatId: String = (loquatName + loquatVersion)

    lazy final val resourceNames: ResourceNames = ResourceNames(loquatId)

    // def managerAutoScalingGroup(keypairName: String): AutoScalingGroup =
    //   AutoScalingGroup(
    //     name = resourceNames.managerGroup,
    //     minSize = 1,
    //     maxSize = 1,
    //     desiredCapacity = 1,
    //     launchConfiguration = LaunchConfiguration(
    //       name = "loquatManagerLaunchConfiguration" + loquatId,
    //       purchaseModel = managerConfig.purchaseModel,
    //       launchSpecs = LaunchSpecs(
    //         managerConfig.instanceSpecs
    //       )(keyName = keypairName,
    //         instanceProfile = Some(iamRoleName)
    //       )
    //     )
    //   )
    //
    // def workersAutoScalingGroup(keypairName: String): AutoScalingGroup =
    //   AutoScalingGroup(
    //     name = resourceNames.workersGroup,
    //     minSize = workersConfig.groupSize.min,
    //     maxSize = workersConfig.groupSize.max,
    //     desiredCapacity = workersConfig.groupSize.desired,
    //     launchConfiguration = LaunchConfiguration(
    //       name = "loquatWorkersLaunchConfiguration" + loquatId,
    //       purchaseModel = workersConfig.purchaseModel,
    //       launchSpecs = LaunchSpecs(
    //         workersConfig.instanceSpecs
    //       )(keyName = keypairName,
    //         instanceProfile = Some(iamRoleName),
    //         deviceMapping = workersConfig.deviceMapping
    //       )
    //     )
    //   )

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
