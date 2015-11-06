package ohnosequences.loquat

protected[loquat] case object daemons {

  import dataMappings._, configs._, utils._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._
  import ohnosequences.statika.results._

  import com.typesafe.scalalogging.LazyLogging
  import scala.collection.mutable.ListBuffer
  import scala.concurrent.duration._

  import ohnosequences.awstools.AWSClients
  import ohnosequences.awstools.s3._
  import com.amazonaws.auth.InstanceProfileCredentialsProvider
  import java.io.File
  import java.util.concurrent._
  import scala.util.Try


  case class LogUploaderBundle(val config: AnyLoquatConfig) extends Bundle() with LazyLogging {

    lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

    val logFile = new File("/root/log.txt")

    val bucket = config.resourceNames.bucket

    def uploadLog(): Unit = Try {
      aws.ec2.getCurrentInstanceId.get
    }.map { id =>
      aws.s3.uploadFile(S3Folder(bucket, config.loquatId) / id, logFile)
      ()
    }.getOrElse {
      logger.error(s"Failed to upload the log to the bucket [${bucket}]")
    }

    def instructions: AnyInstructions = LazyTry[Unit] {
      if (aws.s3.bucketExists(bucket)) {
        schedule(
          after = 30.seconds,
          every = 30.seconds
        )(uploadLog)
        Success("Log uploader daemon started", ())
      }
      else Failure(s"Bucket [${bucket}] doesn't exist")
    }
  }


  case class TerminationDaemonBundle(val config: AnyLoquatConfig) extends Bundle() with LazyLogging {

    lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

    val successResults = scala.collection.mutable.HashMap[String, String]()
    val failedResults = scala.collection.mutable.HashMap[String, String]()

    object TerminationDaemonThread extends Thread("TerminationDaemonBundle") {

      override def run(): Unit = {
        logger.info("TerminationDaemonBundle started")

        while(true) {
          logger.info("TerminationDeaemon conditions: " + config.terminationConfig)
          logger.info("TerminationDeaemon success results: " + successResults.size)
          logger.info("TerminationDeaemon failure results: " + failedResults.size)

          // FIXME: we don't need parsing here, only the numbers of messages
          receiveDataMappingsResults(config.resourceNames.outputQueue).foreach { case (handle, result) =>
            successResults.put(result.id, result.message)
          }

          receiveDataMappingsResults(config.resourceNames.errorQueue).foreach { case (handle, result) =>
            failedResults.put(result.id, result.message)
          }

          val reason = checkConditions(
            terminationConfig = config.terminationConfig,
            successResultsCount = successResults.size,
            failedResultsCount = failedResults.size,
            initialDataMappingsCount = config.dataMappings.length
          )

          reason.foreach{ LoquatOps.undeploy(config, aws, _) }

          Thread.sleep(5.minutes.toMillis)
        }

      }
    }

    def receiveDataMappingsResults(queueName: String): List[(String, ProcessingResult)] = {
      val rawMessages: List[(String, String)] = getQueueMessagesWithHandles(queueName)

      rawMessages.map { case (handle, rawMessageBody) =>
        // TODO: check this:
        val resultDescription: ProcessingResult = upickle.default.read[ProcessingResult](rawMessageBody)
          // upickle.default.read[ProcessingResult](snsMessage.Message.replace("\\\"", "\""))
        logger.info(resultDescription.toString)
        (handle, resultDescription)
      }
    }

    def getQueueMessagesWithHandles(queueName: String): List[(String, String)] = {
      aws.sqs.getQueueByName(queueName) match {
        case None => Nil
        case Some(queue) => {
          var messages = ListBuffer[(String, String)]()
          var empty = false
          var i = 0
          while(!empty && i < 10) {
            val chuck = queue.receiveMessages(10)
            if (chuck.isEmpty) {
              empty = true
            }
            messages ++= chuck.map { m=>
              m.receiptHandle -> m.body
            }
            i += 1
          }
          messages.toList
        }
      }
    }

    def checkConditions(
      terminationConfig: TerminationConfig,
      successResultsCount: Int,
      failedResultsCount: Int,
      initialDataMappingsCount: Int
    ): Option[AnyTerminationReason] = {

      lazy val afterInitial = TerminateAfterInitialDataMappings(
        terminationConfig.terminateAfterInitialDataMappings,
        initialDataMappingsCount,
        successResultsCount
      )
      lazy val tooManyErrors = TerminateWithTooManyErrors(
        terminationConfig.errorsThreshold,
        failedResultsCount
      )
      lazy val globalTimeout = TerminateAfterGlobalTimeout(
        terminationConfig.globalTimeout,
        aws.as.getCreatedTime(config.resourceNames.managerGroup).map{ _.getTime.seconds }
      )

           if (afterInitial.check) Some(afterInitial)
      else if (tooManyErrors.check) Some(tooManyErrors)
      else if (globalTimeout.check) Some(globalTimeout)
      else None
    }

    def instructions: AnyInstructions = say("TerminationDaemonBundle installed")

  }

}
