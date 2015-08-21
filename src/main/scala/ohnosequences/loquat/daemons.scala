package ohnosequences.loquat

case object daemons {

  import dataMappings._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import com.typesafe.scalalogging.LazyLogging
  import scala.collection.mutable.ListBuffer

  import ohnosequences.awstools.AWSClients
  import ohnosequences.awstools.s3.ObjectAddress
  import com.amazonaws.auth.InstanceProfileCredentialsProvider
  import java.io.File


  case class LogUploaderBundle(val config: AnyLoquatConfig) extends Bundle() with LazyLogging {

    lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

    def install: Results = {
      val logFile = new File("/root/log.txt")

      val bucket = config.resourceNames.bucket

      aws.ec2.getCurrentInstanceId match {
        case Some(id) => {
          val logUploader = new Thread(new Runnable {
            def run() {
              while(true) {
                try {
                  if(aws.s3.getBucket(bucket).isEmpty) {
                      logger.warn("bucket " + bucket + " doesn't exist")
                    } else {
                      aws.s3.uploadFile(ObjectAddress(bucket, config.loquatId) / id, logFile)
                    }

                  Thread.sleep(1000 * 30)
                } catch {
                  case t: Throwable => logger.error("log upload fails", t);
                }
              }
            }
          }, "logUploader")
          logUploader.setDaemon(true)
          logUploader.start()
          success("logUploader started")
        }
        case None => failure("can't obtain instanceId")
      }
    }
  }


  case class TerminationDaemonBundle(val config: AnyLoquatConfig) extends Bundle() with LazyLogging {

    lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

    val TIMEOUT = 300 //5 min

    val successResults = scala.collection.mutable.HashMap[String, String]()
    val failedResults = scala.collection.mutable.HashMap[String, String]()

    object TerminationDaemonThread extends Thread("TerminationDaemonBundle") {


      override def run() {
        logger.info("TerminationDaemonBundle started")

        while(true) {
          logger.info("TerminationDeaemon conditions: " + config.terminationConfig)
          logger.info("TerminationDeaemon success results: " + successResults.size)
          logger.info("TerminationDeaemon failed results: " + failedResults.size)

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

          reason match {
            case Some(r) => LoquatOps.undeploy(config)
            case None => ()
          }

          Thread.sleep(TIMEOUT * 1000)
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
    ): Option[String] = {

      val startTime = aws.as.getCreatedTime(config.managerAutoScalingGroup.name).map(_.getTime)

      if (
        terminationConfig.terminateAfterInitialDataMappings &&
        (successResultsCount >= initialDataMappingsCount)
      ) {
        Some("terminated due to terminateAfterInitialDataMappings: initialDataMappings count: " + initialDataMappingsCount + " current: " + successResultsCount)
      } else if (
        terminationConfig.errorsThreshold.map{ failedResultsCount >= _ }.getOrElse(false)
      ) {
        Some("terminated due to errorsThreshold: errorsThreshold count: " + terminationConfig.errorsThreshold.get + " current: " + failedResultsCount)
      } else {
        // TODO: check this
        (startTime, terminationConfig.timeout) match {
          case (None, _) => Some("start timeout is undefined!")
          case (Some(timestamp), Some(timeout)) if ((System.currentTimeMillis() - timestamp) > timeout) => {
            Some("terminated due to global timeout!")
          }
          case _ => None
        }
      }
    }

    def install: Results = {
      success("TerminationDaemonBundle installed")
    }

  }

}
