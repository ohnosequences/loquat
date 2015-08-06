package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import org.clapper.avsl.Logger

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


trait AnyResourcesBundle extends AnyBundle {

  val bundleDependencies: List[AnyBundle] = List()

  type Config <: AnyNisperitoConfig
  val  config: Config

  lazy val resourceNames: ResourceNames = config.resourceNames

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  lazy val logger = Logger(this.getClass)

  def install: Results = {

    logger.info("installing resources")

    logger.info("creating error topic: " + resourceNames.errorTopic)
    val errorTopic = aws.sns.createTopic(resourceNames.errorTopic)
    logger.info("creating error queue: " + resourceNames.errorQueue)
    val errorQueue = aws.sqs.createQueue(resourceNames.errorQueue)
    logger.info("subscribing error queue to error topic")
    errorTopic.subscribeQueue(errorQueue)

    logger.info("creating input queue: " + resourceNames.inputQueue)
    val inputQueue = aws.sqs.createQueue(resourceNames.inputQueue)

    logger.info("creating output topic: " + resourceNames.outputTopic)
    val outputTopic = aws.sns.createTopic(resourceNames.outputTopic)
    logger.info("creating output queue: " + resourceNames.outputQueue)
    val outputQueue = aws.sqs.createQueue(resourceNames.outputQueue)
    logger.info("subscribing output queue to output topic")
    outputTopic.subscribeQueue(outputQueue)

    logger.info("creating notification topic: " + config.notificationTopic)
    val topic = aws.sns.createTopic(config.notificationTopic)

    if (!topic.isEmailSubscribed(config.email)) {
      logger.info("subscribing " + config.email + " to notification topic")
      topic.subscribeEmail(config.email)
      logger.info("please confirm subscription")
    }

    logger.info("creating bucket " + resourceNames.bucket)
    aws.s3.createBucket(config.resourceNames.bucket)

    success("resources bundle installed")
  }

}

abstract class ResourcesBundle[C <: AnyNisperitoConfig](val config: C)
  extends AnyResourcesBundle { type Config = C }
