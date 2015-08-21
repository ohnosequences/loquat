
```scala
package ohnosequences.loquat.bundles

import ohnosequences.loquat.AnyLoquatConfig

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.awstools.s3.ObjectAddress

import com.typesafe.scalalogging.LazyLogging
import java.io.File

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


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