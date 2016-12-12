
```scala
package ohnosequences.loquat.test

import ohnosequences.loquat._
import ohnosequences.awstools._, regions._, ec2._, autoscaling._, s3._
import ohnosequences.statika._, aws._
import test.dataProcessing._

case object config {

  val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

  case object testConfig extends AnyLoquatConfig { config =>
    val loquatName = "experimental"

    // TODO: create a role for testing loquat
    val iamRoleName = "loquat.testing"
    val logsS3Prefix = s3"loquat.testing" /

    val metadata: AnyArtifactMetadata = ohnosequences.generated.metadata.loquat

    val  managerConfig = ManagerConfig(
      defaultAMI,
      m3.medium,
      PurchaseModel.spot(0.1)
    )

    val workersConfig = WorkersConfig(
      defaultAMI,
      m3.medium,
      PurchaseModel.spot(0.1),
      AutoScalingGroupSize(0, 1, 20)
    )

    val terminationConfig = TerminationConfig(
      terminateAfterInitialDataMappings = true
    )

    override val checkInputObjects = false
  }

  val N = 100
  val dataMappings: List[DataMapping[processingBundle.type]] = (1 to N).toList.map{ _ => test.dataMappings.dataMapping }

  case object testLoquat extends Loquat(testConfig, processingBundle)(dataMappings)

  val testUser = LoquatUser(
    email = "aalekhin@ohnosequences.com",
    localCredentials = new com.amazonaws.auth.profile.ProfileCredentialsProvider("default"),
    keypairName = "aalekhin"
  )

}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/awsClients.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../../main/scala/ohnosequences/loquat/dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../../../../../main/scala/ohnosequences/loquat/dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../../../../../main/scala/ohnosequences/loquat/logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../../../../../main/scala/ohnosequences/loquat/loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../../../../../main/scala/ohnosequences/loquat/manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../../../../../main/scala/ohnosequences/loquat/terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../../../../../main/scala/ohnosequences/loquat/utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../../../../../main/scala/ohnosequences/loquat/worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: md5.scala.md