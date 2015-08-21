
```scala
package ohnosequences.loquat.bundles

import ohnosequences.loquat._, dataMappings._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


// We don't want it to be used outside of this project
protected[loquat]
trait AnyManagerBundle extends AnyBundle with LazyLogging { manager =>

  val fullName: String

  type Worker <: AnyWorkerBundle
  val  worker: Worker

  val config = worker.config

  case object workerCompat extends Compatible[Worker#Config#AMI, Worker](
    environment = config.ami,
    bundle = worker,
    metadata = config.metadata
  ) {
    override lazy val fullName: String = s"${manager.fullName}.${this.toString}"
  }

  val terminationDaemon = TerminationDaemonBundle(config)

  val bundleDependencies: List[AnyBundle] = List(terminationDaemon, LogUploaderBundle(config))

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  def uploadInitialDataMappings(dataMappings: List[AnyDataMapping]) {
    try {
      logger.info("adding initial dataMappings to SQS")
      // FIXME: match on Option instead of get
      val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get

      // logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")

      // NOTE: we can send messages in parallel
      dataMappings.par.foreach { dataMapping =>
        inputQueue.sendMessage(upickle.default.write[SimpleDataMapping](simplify(dataMapping)))
      }
      aws.s3.uploadString(config.dataMappingsUploaded, "")
      logger.info("initial dataMappings are ready")
    } catch {
      case t: Throwable => logger.error("error during uploading initial dataMappings", t)
    }
  }


  def install: Results = {

    logger.info("manager is started")

    try {
      logger.info("checking if the initial dataMappings are uploaded")
      if (aws.s3.listObjects(config.dataMappingsUploaded.bucket, config.dataMappingsUploaded.key).isEmpty) {
        logger.warn("uploading initial dataMappings")
        uploadInitialDataMappings(config.dataMappings)
      } else {
        logger.warn("skipping uploading dataMappings")
      }
    } catch {
      case t: Throwable => logger.error("error during uploading initial dataMappings", t)
    }

    try {
      logger.info("generating workers userScript")
      val workersGroup = aws.as.fixAutoScalingGroupUserData(
        config.workersAutoScalingGroup,
        workerCompat.userScript
      )

      logger.info("running workers auto scaling group")
      aws.as.createAutoScalingGroup(workersGroup)

      val groupName = config.workersAutoScalingGroup.name

      utils.waitForResource[AutoScalingGroup] {
        println("waiting for manager autoscalling")
        aws.as.getAutoScalingGroupByName(groupName)
      }

      logger.info("creating tags")
      utils.tagAutoScalingGroup(aws.as, groupName, InstanceTags.INSTALLING.value)
    } catch {
      case t: Throwable => logger.error("error during creating workers autoscaling group", t)
    }

    try {
      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()
    } catch {
      case t: Throwable => logger.error("error during starting termination daemon", t)
    }

    success("manager installed")
    // } catch {
    //   case t: Throwable => {
    //     t.printStackTrace()
    //     aws.ec2.getCurrentInstance.foreach(_.terminate)
    //     failure("manager fails")
    //   }
    // }
  }
}

protected[loquat]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W) extends AnyManagerBundle {

  type Worker = W
}

```




[main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala]: InstructionsBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala]: LogUploaderBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala]: ManagerBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala]: TerminationDaemonBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala]: WorkerBundle.scala.md
[main/scala/ohnosequences/nisperito/Config.scala]: ../Config.scala.md
[main/scala/ohnosequences/nisperito/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/nisperito/Nisperito.scala]: ../Nisperito.scala.md
[main/scala/ohnosequences/nisperito/Utils.scala]: ../Utils.scala.md
[test/scala/ohnosequences/nisperito/dataMappings.scala]: ../../../../../test/scala/ohnosequences/nisperito/dataMappings.scala.md
[test/scala/ohnosequences/nisperito/instructions.scala]: ../../../../../test/scala/ohnosequences/nisperito/instructions.scala.md