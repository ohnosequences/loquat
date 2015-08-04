package ohnosequences.nispero.bundles

import ohnosequences.nispero._
import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.awstools.dynamodb._
import ohnosequences.logging._
import org.clapper.avsl.Logger

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


abstract class ResourcesBundle extends Bundle() {

  type Config <: AnyConfig
  val  config: Config

  lazy val resourceNames: ResourceNames = config.resourceNames

  lazy val awsClients: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  lazy val logger = Logger(this.getClass)

  def install: Results = {

    logger.info("installing resources")

    logger.info("creating error topic: " + resourceNames.errorTopic)
    val errorTopic = awsClients.sns.createTopic(resourceNames.errorTopic)
    logger.info("creating error queue: " + resourceNames.errorQueue)
    val errorQueue = awsClients.sqs.createQueue(resourceNames.errorQueue)
    logger.info("subscribing error queue to error topic")
    errorTopic.subscribeQueue(errorQueue)

    logger.info("creating input queue: " + resourceNames.inputQueue)
    val inputQueue = awsClients.sqs.createQueue(resourceNames.inputQueue)

    logger.info("creating control queue: " + resourceNames.controlQueue)
    awsClients.sqs.createQueue(resourceNames.controlQueue)

    logger.info("creating output topic: " + resourceNames.outputTopic)
    val outputTopic = awsClients.sns.createTopic(resourceNames.outputTopic)
    logger.info("creating output queue: " + resourceNames.outputQueue)
    val outputQueue = awsClients.sqs.createQueue(resourceNames.outputQueue)
    logger.info("subscribing output queue to output topic")
    outputTopic.subscribeQueue(outputQueue)

    logger.info("creating notification topic: " + config.notificationTopic)
    val topic = awsClients.sns.createTopic(config.notificationTopic)

    if (!topic.isEmailSubscribed(config.email)) {
      logger.info("subscribing " + config.email + " to notification topic")
      topic.subscribeEmail(config.email)
      logger.info("please confirm subscription")
    }

    logger.info("creating bucket " + resourceNames.bucket)
    awsClients.s3.createBucket(config.resourceNames.bucket)

    logger.info("creating farm state table")
    DynamoDBUtils.createTable(
      ddb = awsClients.ddb,
      tableName = config.resourceNames.workersStateTable,
      hash = Names.Tables.WORKERS_STATE_HASH_KEY,
      range = Some(Names.Tables.WORKERS_STATE_RANGE_KEY),
      logger = new ConsoleLogger(config.resourceNames.workersStateTable)
    )

    success("resources bundle installed")
  }

}
