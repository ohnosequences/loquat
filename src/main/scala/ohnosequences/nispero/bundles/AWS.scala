package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._, amazonLinuxAMIs._

import ohnosequences.nispero.Config

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper


abstract class AWSBundle extends Bundle() {

  val metadata: AnyArtifactMetadata

  type AMI <: AmazonLinuxAMI
  val  ami: AMI

  val config: Config

  // TODO: region should be configurable
  val clients: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())
  val dynamoMapper: DynamoDBMapper = new DynamoDBMapper(clients.ddb)

  def install: Results = {
    success("aws installed")
  }
}
