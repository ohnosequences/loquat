
```scala
package ohnosequences.loquat

import utils._

import ohnosequences.statika.AnyArtifactMetadata
import ohnosequences.statika.aws._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.ec2.AnyAmazonLinuxAMI
import ohnosequences.awstools.s3._

import ohnosequences.datasets._

import ohnosequences.cosas._, types._

import better.files._

import scala.util.Try
import collection.JavaConversions._
```

Configuration for loquat

```scala
abstract class AnyLoquatConfig extends AnyConfig {
  // TODO: limit allowed symbols and check on validation
  val loquatName: String
```

IAM role that will be used by the autoscaling groups

```scala
  val iamRoleName: String
```

An S3 bucket for saving logs

```scala
  val logsBucketName: String
```

Metadata generated for your loquat project

```scala
  val metadata: AnyArtifactMetadata

  val managerConfig: AnyManagerConfig
  val workersConfig: AnyWorkersConfig

  val terminationConfig: TerminationConfig

  val dataMappings: List[AnyDataMapping]
```

This setting switches the check of existence of the input S3 objects

```scala
  val checkInputObjects: Boolean = true
```

This setting determines whether empty output files will be uploaded or not

```scala
  val skipEmptyResults: Boolean = true
```

Here follow all the values that are dependent on those defined on top

```scala
  lazy val configLabel: String = s"${loquatName} config"

  lazy val ami: AnyAmazonLinuxAMI = managerConfig.instanceSpecs.ami
  lazy val amiEnv: AnyLinuxAMIEnvironment = amznAMIEnv(ami)
  lazy val region: Region = ami.region

  lazy final val fatArtifactS3Object: S3Object = {
    val s3url = """s3://(.+)/(.+)""".r
    metadata.artifactUrl match {
      case s3url(bucket, key) => S3Object(bucket, key)
      case _ => throw new Error("Wrong fat jar url, it should be an S3 address")
    }
  }
```

Unique id  of the loquat instance

```scala
  lazy final val artifactName: String = metadata.artifact.replace(".", "-").toLowerCase
  lazy final val artifactVersion: String = metadata.version.replace(".", "-").toLowerCase
  lazy final val loquatId: String = s"${loquatName}-${artifactName}-${artifactVersion}"

  lazy final val resourceNames: ResourceNames = ResourceNames(loquatId, logsBucketName)



  lazy final val subConfigs: List[AnyConfig] = List(
    managerConfig,
    workersConfig
  )

  def validationErrors(aws: AWSClients): Seq[String] = {

    logger.info("Checking that data mappings define all the needed data keys...")
    dataMappings.find {
      _.checkDataKeys.nonEmpty
    } match {

      case Some(dm) => dm.checkDataKeys

      case _ => {

        logger.info("Checking the fat-artifact existence...")
        if (aws.s3.objectExists(fatArtifactS3Object).isFailure) {
          Seq(s"Couldn't access the artifact at [${fatArtifactS3Object.url}] (probably you forgot to publish it)")
        } else if(checkInputObjects) {

          logger.info("Checking input S3 objects existence...")

          print("[")

          val errors: Seq[String] = dataMappings flatMap { dataMapping =>

            // if an input object doesn't exist, we return an arror message
            dataMapping.remoteInput flatMap {
              case (dataKey, S3Resource(s3address)) => {
                val exists: Boolean = Try(
                  aws.s3.s3.listObjects(s3address.bucket, s3address.key).getObjectSummaries
                ).filter{ _.length > 0 }.isSuccess

                if (exists) print("+") else print("-")
                // logger.debug(s"[${dataMapping.id}]: [${dataKey.label}] -> [${s3address.url}] ${if(exists) "exists" else "DOESN'T exist!"}")

                if (exists) None
                else Some(s"Input object [${dataKey.label}] doesn't exist at the address: [${s3address.url}]")
              }
              // if the mapping is not an S3Resource, we don't check
              case _ => None
            }
          }

          println("]")

          errors

        } else Seq()
      }
    }
  }

}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../../test/scala/ohnosequences/loquat/test/md5.scala.md