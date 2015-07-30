package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._

// import ohnosequences.awstools.ec2.EC2
// import ohnosequences.awstools.s3.S3
// import ohnosequences.awstools.autoscaling.AutoScaling
// import ohnosequences.awstools.sqs.SQS
// import ohnosequences.awstools.sns.SNS
// import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


abstract class AWS(val configuration: Configuration) extends Bundle(configuration) {

  // val awsCredentials: AWSCredentials = RoleCredentials

  // val ec2 = EC2.create()
  // val s3 = S3.create()
  // val as = AutoScaling.create(ec2)
  // val sqs = SQS.create()
  // val sns = SNS.create()
  // val ddb = DynamoDB.create()
  // val dynamoMapper = dynamoDB.createMapper

  // TODO: region should be configurable
  val clients: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())
  //   val ec2 = ec2
  //   val as = as
  //   val sqs = sqs
  //   val sns = sns
  //   val s3 = s3
  //   val ddb = dynamoDB
  // }

  def install: Results = {
    success("aws installed")
  }
}
