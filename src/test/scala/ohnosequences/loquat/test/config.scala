package ohnosequences.loquat.test

import ohnosequences.loquat._
import ohnosequences.awstools._, regions._, ec2._, autoscaling._
import ohnosequences.statika._, aws._
import test.dataProcessing._

case object config {

  val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

  case object testConfig extends AnyLoquatConfig { config =>
    val loquatName = "experimental"

    // TODO: create a role for testing loquat
    val iamRoleName = "loquat.testing"
    val logsBucketName = "loquat.testing"

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

  val N = 1000
  val dataMappings: List[DataMapping[processingBundle.type]] = (1 to N).toList.map{ _ => test.dataMappings.dataMapping }

  case object testLoquat extends Loquat(testConfig, processingBundle)(dataMappings)

  val testUser = LoquatUser(
    email = "aalekhin@ohnosequences.com",
    localCredentials = new com.amazonaws.auth.profile.ProfileCredentialsProvider("default"),
    keypairName = "aalekhin"
  )

}
