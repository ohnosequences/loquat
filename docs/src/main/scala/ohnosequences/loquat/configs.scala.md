
```scala
package ohnosequences.loquat

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
import scala.concurrent.duration._
```

Any config here can validate itself (in runtime)

```scala
trait AnyConfig extends LazyLogging {

  lazy private val label: String = this.toString

  def validationErrors: Seq[String]
```

Config dependencies

```scala
  val subConfigs: Seq[AnyConfig]
```

This method validates subconfigs and logs validation errors

```scala
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
```

Preferred availability zones, if empty, set to all available zones

```scala
  val availabilityZones: List[String]

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
      case Spot(Some(price), Some(delta)) if price <= 0 || delta < 0 =>
        Seq(s"Spot price has to be positive: ${price}")
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
        name = groupName + "-launchConfiguration",
        purchaseModel = conf.purchaseModel,
        launchSpecs = LaunchSpecs(
          conf.instanceSpecs
        )(keyName = keypairName,
          instanceProfile = Some(iamRoleName),
          deviceMapping = conf.deviceMapping
        )
      ),
      availabilityZones = conf.availabilityZones
    )

}
```

Manager autoscaling group configuration

```scala
trait AnyManagerConfig extends AnyAutoScalingConfig {

  val groupSize = AutoScalingGroupSize(1, 1, 1)
  val deviceMapping = Map[String, String]()
}

case class ManagerConfig[
  IS <: AnyInstanceSpecs,
  PM <: AnyPurchaseModel
](instanceSpecs: IS,
  purchaseModel: PM,
  availabilityZones: List[String] = List()
) extends AnyManagerConfig {

  type InstanceSpecs = IS
  type PurchaseModel = PM
}
```

Workers autoscaling group configuration

```scala
trait AnyWorkersConfig extends AnyAutoScalingConfig

case class WorkersConfig[
  IS <: AnyInstanceSpecs,
  PM <: AnyPurchaseModel
](instanceSpecs: IS,
  purchaseModel: PM,
  groupSize: AutoScalingGroupSize,
  availabilityZones: List[String] = List(),
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

  def msg: String = s"""|Termination after successfully processing all the initial data mappings.
    |  Initial data mappings count: ${initialCount}
    |  Successful results: ${successfulCount}
    |""".stripMargin
}

case class TerminateWithTooManyErrors(
  val errorsThreshold: Option[Int],
  val failedCount: Int
) extends AnyTerminationReason {

  def check: Boolean = errorsThreshold.map{ failedCount >= _ }.getOrElse(false)

  def msg: String = s"""|Termination due to too many errors.
    |  Errors threshold: ${errorsThreshold}
    |  Failed results count: ${failedCount}
    |""".stripMargin
}

case class TerminateAfterGlobalTimeout(
  val globalTimeout: Option[FiniteDuration],
  val startTime: Option[FiniteDuration]
) extends AnyTerminationReason {

  def check: Boolean = (startTime, globalTimeout) match {
    case (Some(timestamp), Some(globalTimeout)) =>
      (System.currentTimeMillis - timestamp.toMillis) > globalTimeout.toMillis
    case _ => false
  }

  def msg: String = s"Termination due to the global timeout: ${globalTimeout.getOrElse(0.seconds)}"
}

case object TerminateManually extends AnyTerminationReason {
  def check: Boolean = true
  def msg: String = "Manual termination"
}
```

Configuration of termination conditions

```scala
case class TerminationConfig(
  // if true loquat will terminate after solving all initial tasks
  terminateAfterInitialDataMappings: Boolean,
  // if true loquat will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int] = None,
  // maximum time for processing one task
  taskProcessingTimeout: Option[FiniteDuration] = None,
  // maximum time for everything
  globalTimeout: Option[FiniteDuration] = None
) extends Config() {

  def validationErrors: Seq[String] = {
    val treshholdErr = errorsThreshold match {
      case Some(n) if n <= 0 => Seq(s"Errors threshold has to be positive: ${n}")
      case _ => Seq()
    }

    val localTimeoutErr = taskProcessingTimeout match {
      case Some(time) if (
          time <= 0.seconds ||
          time > 12.hours
        ) => Seq(s"Task processing timeout [${time}] has to be between 0 seconds and 12 hours")
      case _ => Seq()
    }

    val globalTimeoutErr = globalTimeout match {
      case Some(time) if (
          time <= 0.seconds ||
          time > 12.hours
        ) => Seq(s"Global timeout [${time}] has to be between 0 seconds and 12 hours")
      case _ => Seq()
    }

    treshholdErr ++ localTimeoutErr ++ globalTimeoutErr
  }
}
```

Configuration of resources

```scala
private[loquat]
case class ResourceNames(prefix: String, bucketName: String) {
```

name of queue with dataMappings

```scala
  val inputQueue: String = prefix + "-loquat-input"
```

name of topic for dataMappings result notifications

```scala
  val outputQueue: String = prefix + "-loquat-output"
```

name of queue with errors (will be subscribed to errorTopic)

```scala
  val errorQueue: String = prefix + "-loquat-errors"
```

name of bucket for logs files

```scala
  val bucket: String = bucketName
```

topic name to notificate user about termination of loquat

```scala
  val notificationTopic: String = prefix + "-loquat-notifications"
```

name of the manager autoscaling group

```scala
  val managerGroup: String = prefix + "-loquat-manager"
```

name of the workers autoscaling group

```scala
  val workersGroup: String = prefix + "-loquat-workers"
}
```

Simple type to separate user-related data from the config

```scala
case class LoquatUser(
```

email address for notifications

```scala
  val email: String,
```

these are credentials that are used to launch loquat

```scala
  val localCredentials: AWSCredentialsProvider,
```

keypair name for connecting to the loquat instances

```scala
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
```

Configuration for loquat

```scala
abstract class AnyLoquatConfig extends AnyConfig {
  // TODO: limit allowed symbols and check on validation
  val loquatName: String
```

Metadata generated for your loquat project

```scala
  val metadata: AnyArtifactMetadata
```

AMI that will be used for manager and worker instances

```scala
  // NOTE: we need the AMI type here for the manager/worker compats
  type AMIEnv <: AnyLinuxAMIEnvironment
  val  amiEnv: AMIEnv
```

IAM rolse that will be used by the autoscaling groups

```scala
  val iamRoleName: String
  val bucketName: String

  type ManagerConfig <: AnyManagerConfig
  val  managerConfig: ManagerConfig

  type WorkersConfig <: AnyWorkersConfig
  val  workersConfig: WorkersConfig
```

Termination conditions

```scala
  val terminationConfig: TerminationConfig
```

List of tiny or big dataMappings

```scala
  val dataMappings: List[AnyDataMapping]

  // TODO: AWS region should be also configurable

```

Here follow all the values that are dependent on those defined on top

```scala
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
```

Unique id  of the loquat instance

```scala
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
  //   val s3  = S3.create(creds)
  //   val artifactErr =
  //     if (s3.objectExists(fatArtifactS3Object).isSuccess) Seq()
  //     else Seq(s"Couldn't access the artifact at [${fatArtifactS3Object.url}] (probably you forgot to publish it)")
  // }
}

```




[main/scala/ohnosequences/loquat/configs.scala]: configs.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md