package ohnosequences.loquat.test

import ohnosequences.loquat._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._
import ohnosequences.statika._, aws._

case object config {

  val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

  case object testConfig extends AnyLoquatConfig { config =>
    val bucketName = "loquat.testing"
    val loquatName = "experimental"

    // TODO: create a role for testing loquat
    val iamRoleName: String = "loquat.testing"

    type AMIEnv = amznAMIEnv[defaultAMI.type]
    val  amiEnv = amznAMIEnv(defaultAMI): AMIEnv

    val metadata: AnyArtifactMetadata = generated.metadata.Loquat

    type ManagerConfig = AnyManagerConfig
    val  managerConfig = ManagerConfig(
      InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.1))
    )

    type WorkersConfig = AnyWorkersConfig
    val workersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.1)),
      groupSize = AutoScalingGroupSize(0, 1, 1)
    )

    val terminationConfig = TerminationConfig(
      terminateAfterInitialDataMappings = true
    )

    val N = 100
    val dataMappings: List[AnyDataMapping] = (1 to N).toList.map{ _ => test.dataMappings.dataMapping }
  }

  case object testLoquat extends Loquat(testConfig, test.dataProcessing.processingBundle)

  val testUser = LoquatUser(
    email = "aalekhin@ohnosequences.com",
    localCredentials = new com.amazonaws.auth.profile.ProfileCredentialsProvider("default"),
    keypairName = "aalekhin"
  )

}
