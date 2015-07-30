package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.nispero.{Names}

import org.clapper.avsl.Logger

abstract class Resources(val configuration: Configuration, aws: AWS) extends Bundle(configuration, aws) {

  val resources = configuration.config.resources

  val config = configuration.config

  val logger = Logger(this.getClass)

  def install: Results = {

    logger.info("installing resources")

    logger.info("creating error topic: " + resources.errorTopic)
    val errorTopic = aws.clients.sns.createTopic(resources.errorTopic)
    logger.info("creating error queue: " + resources.errorQueue)
    val errorQueue = aws.clients.sqs.createQueue(resources.errorQueue)
    logger.info("subscribing error queue to error topic")
    errorTopic.subscribeQueue(errorQueue)

    logger.info("creating input queue: " + resources.inputQueue)
    val inputQueue = aws.clients.sqs.createQueue(resources.inputQueue)

    logger.info("creating control queue: " + resources.controlQueue)
    aws.clients.sqs.createQueue(resources.controlQueue)

    logger.info("creating output topic: " + resources.outputTopic)
    val outputTopic = aws.clients.sns.createTopic(resources.outputTopic)
    logger.info("creating output queue: " + resources.outputQueue)
    val outputQueue = aws.clients.sqs.createQueue(resources.outputQueue)
    logger.info("subscribing output queue to output topic")
    outputTopic.subscribeQueue(outputQueue)

    logger.info("creating notification topic: " + config.notificationTopic)
    val topic = aws.clients.sns.createTopic(config.notificationTopic)

    if (!topic.isEmailSubscribed(config.email)) {
      logger.info("subscribing " + config.email + " to notification topic")
      topic.subscribeEmail(config.email)
      logger.info("please confirm subscription")
    }

    logger.info("creating bucket " + resources.bucket)
    aws.clients.s3.createBucket(config.resources.bucket)

    logger.info("creating farm state table")
    DynamoDBUtils.createTable(
      ddb = aws.clients.ddb,
      tableName = config.resources.workersStateTable,
      hash = Names.Tables.WORKERS_STATE_HASH_KEY,
      range = Some(Names.Tables.WORKERS_STATE_RANGE_KEY)
      logger = logger
    )

    success("resources bundle finished")

  }

}
