package ohnosequences.loquat

import com.amazonaws.auth._
import com.amazonaws.regions._
import ohnosequences.awstools._, sqs._, sns._, s3._, ec2._, autoscaling._, regions._


case class AWSClients(
  region:      AwsRegionProvider      = new DefaultAwsRegionProviderChain(),
  credentials: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain()
) {

  lazy val ec2 = EC2Client(region, credentials)
  lazy val sns = SNSClient(region, credentials)
  lazy val sqs = SQSClient(region, credentials)
  lazy val  s3 = S3Client(region, credentials)
  lazy val  as = AutoScalingClient(region, credentials)
}
