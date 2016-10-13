package ohnosequences.loquat

import com.amazonaws.auth.AWSCredentialsProvider
import ohnosequences.awstools._, sqs._, sns._, s3._, ec2._
import ohnosequences.awstools.regions.Region
import ohnosequences.awstools.autoscaling.AutoScaling


case class AWSClients(
  credentialsProvider: AWSCredentialsProvider,
  region: Region
) {
  lazy val ec2 = EC2Client(region, credentialsProvider)
  lazy val sns = SNSClient(region, credentialsProvider)
  lazy val sqs = SQSClient(region, credentialsProvider)
  lazy val  s3 =  S3Client(region, credentialsProvider)

  lazy val as = AutoScaling.create(credentialsProvider, ec2, region)
}
