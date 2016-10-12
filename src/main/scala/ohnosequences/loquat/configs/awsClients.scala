package ohnosequences.loquat

import com.amazonaws.auth.AWSCredentialsProvider
import ohnosequences.awstools.sqs._
import ohnosequences.awstools.sns._
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.ec2.EC2
import ohnosequences.awstools.autoscaling.AutoScaling


case class AWSClients(
  credentialsProvider: AWSCredentialsProvider,
  region: Region
) {

  lazy val ec2 = EC2.create(credentialsProvider, region)
  lazy val as = AutoScaling.create(credentialsProvider, ec2, region)

  // FIXME: different parameters order:
  lazy val sns = ohnosequences.awstools.sns.client(region = region, credentials = credentialsProvider)
  lazy val sqs = ohnosequences.awstools.sqs.client(credentials = credentialsProvider, region = region)
  lazy val s3 = ohnosequences.awstools.s3.client(credentialsProvider, region)
}
