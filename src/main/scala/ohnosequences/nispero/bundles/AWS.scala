package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper


abstract class AWS(val configuration: Configuration) extends Bundle(configuration) {

  // TODO: region should be configurable
  val clients: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())
  val dynamoMapper: DynamoDBMapper = new DynamoDBMapper(clients.ddb)

  def install: Results = {
    success("aws installed")
  }
}
