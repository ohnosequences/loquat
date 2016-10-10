package ohnosequences.loquat

import com.amazonaws.auth.AWSCredentialsProvider
import ohnosequences.awstools, awstools.sqs._
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.ec2.EC2
import ohnosequences.awstools.autoscaling.AutoScaling
import ohnosequences.awstools.sns.SNS


case class AWSClients(
  credentialsProvider: AWSCredentialsProvider,
  region: Region
) {

  lazy val ec2 = EC2.create(credentialsProvider, region)
  lazy val as = AutoScaling.create(credentialsProvider, ec2, region)
  lazy val sns = SNS.create(credentialsProvider, region)

  lazy val sqs = SQS.create(credentialsProvider, region)
  // lazy val sqs = awstools.sqs.client(credentials = credentialsProvider, region = region)
  lazy val s3 = awstools.s3.client(credentialsProvider, region)
}
