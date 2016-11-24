package ohnosequences.loquat

import ohnosequences.awstools.ec2._
import ohnosequences.awstools.autoscaling._
import com.amazonaws.services.autoscaling.model._


trait AnyAutoScalingConfig extends AnyConfig { conf =>

  val subConfigs: Seq[AnyConfig] = Seq()

  type InstanceType <: AnyInstanceType
  val  instanceType: InstanceType

  type AMI <: AnyAmazonLinuxAMI
  val  ami: AMI

  /* We want to ensure that the instance type supports the given AMI at compile time */
  implicit val supportsAMI: InstanceType SupportsAMI AMI


  val purchaseModel: PurchaseModel

  val groupSize: AutoScalingGroupSize

  /* Preferred availability zones, if empty, set to all available zones */
  val availabilityZones: Set[String]

  // TODO: use some better type for this
  val deviceMapping: Map[String, String]

  def validationErrors(aws: AWSClients): Seq[String] = {

    val groupSizeErros: Seq[String] = {
      if ( groupSize.min < 0 ) Seq(s"Minimal autoscaling group size has to be non-negative: ${groupSize.min}")
      else if (
        groupSize.desired < groupSize.min ||
        groupSize.desired > groupSize.max
      ) Seq(s"Desired capacity [${groupSize.desired}] has to be in the interval [${groupSize.min}, ${groupSize.max}]")
      else Seq()
    }

    val purchaseModelErrors: Seq[String] = purchaseModel.maxPrice match {
      case Some(price) if (price <= 0) => Seq(s"Spot price has to be positive: ${price}")
      case _ => Seq()
    }

    groupSizeErros ++ purchaseModelErrors
  }

}


/* Manager autoscaling group configuration */
trait AnyManagerConfig extends AnyAutoScalingConfig

case class ManagerConfig[
  T <: AnyInstanceType,
  A <: AnyAmazonLinuxAMI
](ami: A,
  instanceType: T,
  purchaseModel: PurchaseModel,
  availabilityZones: Set[String] = Set()
)(implicit
  val supportsAMI: T SupportsAMI A
) extends AnyManagerConfig {
  val configLabel = "Manager config"

  type AMI = A
  type InstanceType = T

  val groupSize = AutoScalingGroupSize(1, 1, 1)
  val deviceMapping = Map[String, String]()
}

/* Workers autoscaling group configuration */
trait AnyWorkersConfig extends AnyAutoScalingConfig

case class WorkersConfig[
  T <: AnyInstanceType,
  A <: AnyAmazonLinuxAMI
](ami: A,
  instanceType: T,
  purchaseModel: PurchaseModel,
  groupSize: AutoScalingGroupSize,
  availabilityZones: Set[String] = Set(),
  deviceMapping: Map[String, String] = Map("/dev/sdb" -> "ephemeral0")
)(implicit
  val supportsAMI: T SupportsAMI A
) extends AnyWorkersConfig {
  val configLabel = "Workers config"

  type AMI = A
  type InstanceType = T
}
