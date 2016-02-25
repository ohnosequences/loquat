
```scala
package ohnosequences.loquat.test

import ohnosequences.loquat._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._
import ohnosequences.statika._, aws._

case object config {

  val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

  case object testConfig extends AnyLoquatConfig { config =>
    val loquatName = "experimental"

    // TODO: create a role for testing loquat
    val iamRoleName = "loquat.testing"
    val logsBucketName = "loquat.testing"

    // type AMI = defaultAMI.type
    // lazy val ami: AMI = defaultAMI

    val metadata: AnyArtifactMetadata = generated.metadata.Loquat

    val  managerConfig = ManagerConfig(
      InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.1))
    )

    val workersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.1)),
      groupSize = AutoScalingGroupSize(0, 10, 20)
    )

    val terminationConfig = TerminationConfig(
      terminateAfterInitialDataMappings = true
    )

    val N = 5000
    val dataMappings: List[AnyDataMapping] = (1 to N).toList.map{ _ => test.dataMappings.dataMapping }

    val checkInputObjects = false
  }

  case object testLoquat extends Loquat(testConfig, test.dataProcessing.processingBundle)

  val testUser = LoquatUser(
    email = "aalekhin@ohnosequences.com",
    localCredentials = new com.amazonaws.auth.profile.ProfileCredentialsProvider("default"),
    keypairName = "aalekhin"
  )

}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/autoscaling.scala.md
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