package ohnosequences.nispero

import ohnosequences.statika.bundles._
import ohnosequences.statika.aws._
import ohnosequences.nispero.bundles._
import ohnosequences.nispero.utils.Utils

import com.amazonaws.AmazonServiceException
import org.clapper.avsl.Logger
import ohnosequences.awstools.ec2.{EC2, Tag}
import ohnosequences.awstools.autoscaling.{AutoScalingGroup, AutoScaling}
import ohnosequences.awstools.sns.SNS
import ohnosequences.awstools.AWSClients
import java.io.File
import com.amazonaws.auth.{InstanceProfileCredentialsProvider, PropertiesCredentials, AWSCredentialsProvider}
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.AmazonClientException
import ohnosequences.awstools.s3.S3


abstract class NisperoRunner[
  C <: AnyNisperoConfig,
  M <: AnyManagerBundle
](val config: C, val manager: M) {

  type Config = C
  // val  config: Config = nisperoDistribution.manager.resources.config

  type Manager = M
  // val  manager: Manager

  case object managerCompat extends Compatible(config.ami, manager, config.metadata)

  def compilerChecks(): Unit

  def main(args: Array[String]): Unit = args.toList match {
    case "undeploy" :: tail => undeploy("manual undeploy", tail)
    case list => deploy(args.toList)
  }

  def retrieveCredentialsProviderFromFile(file: File): (AWSCredentialsProvider, Option[String]) = {
    logger.info("retrieving credentials from: " + file.getPath)
    (new StaticCredentialsProvider(new PropertiesCredentials(file)), Some(file.getPath))
  }

  def retrieveCredentialsProvider(args: List[String]): (AWSCredentialsProvider, Option[String]) = {
    args.reverse match {
      case Nil => {
        logger.info("trying to retrieve IAM credentials")
        val iamProvider = new InstanceProfileCredentialsProvider()
        try {
          val ec2 = EC2.create(iamProvider)
          val size = ec2.ec2.describeSpotPriceHistory().getSpotPriceHistory.size()
          (iamProvider, None)
        } catch {
          case e: AmazonClientException => {
            val defaultLocation = System.getProperty("user.home")
            val file = new File(defaultLocation, "nispero.credentials")
            retrieveCredentialsProviderFromFile(file)
          }
        }
      }
      case head :: tail => retrieveCredentialsProviderFromFile(new File(head))
    }
  }

  val logger = Logger(this.getClass)


  def checkConfig(config: Config, ec2: EC2, s3: S3): Boolean = {

    val workers = config.workersAutoScalingGroup

    if (workers.desiredCapacity < workers.minSize || workers.desiredCapacity > workers.maxSize) {
      logger.error("desired capacity should be in interval [minSize, maxSize]")
      return false
    }

    val keyPair = workers.launchingConfiguration.instanceSpecs.keyName
    if(!ec2.isKeyPairExists(keyPair)) {
      logger.error("key pair: " + keyPair + " doesn't exists")
      return false
    }

    try {
      s3.getObjectStream(config.fatArtifactS3Object) match {
        case null => logger.error("artifact isn't uploaded"); false
        case _ => true
      }
    } catch {
      case s3e: AmazonServiceException if s3e.getStatusCode==301 => true
      case t: Throwable  => {
        logger.error("artifact isn't uploaded: " + config.fatArtifactS3Object + " " + t)
        false
      }
    }
  }

  def deploy(args: List[String]) {

    val credentialsProvider = retrieveCredentialsProvider(args)._1
    val ec2 = EC2.create(credentialsProvider)
    val as = AutoScaling.create(credentialsProvider, ec2)
    val sns = SNS.create(credentialsProvider)
    val s3 = S3.create(credentialsProvider)

    if(!checkConfig(config, ec2, s3)) {
      return
    }

    s3.createBucket(config.resourceNames.bucket)

    logger.info("creating notification topic: " + config.notificationTopic)

    val topic = sns.createTopic(config.notificationTopic)

    if (!topic.isEmailSubscribed(config.email)) {
      logger.info("subscribing " + config.email + " to notification topic")
      topic.subscribeEmail(config.email)
      logger.info("please confirm subscription")
    }

    logger.info("generating userScript for manager")
    val managerUserData = managerCompat.userScript

    logger.info("running manager auto scaling group")
    val managerGroup = as.fixAutoScalingGroupUserData(config.managerAutoScalingGroup, managerUserData)
    as.createAutoScalingGroup(managerGroup)

    logger.info("creating tags")
    Utils.tagAutoScalingGroup(as, managerGroup.name, "manager")
  }

  def undeploy(message: String, args: List[String]) {
    val aws = AWSClients.create(retrieveCredentialsProvider(args)._1)
    Undeployer.undeploy(aws, config, message)
  }

}
