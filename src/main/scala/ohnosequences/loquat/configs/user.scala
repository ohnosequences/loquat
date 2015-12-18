package ohnosequences.loquat

import ohnosequences.awstools.ec2.EC2

import com.amazonaws.auth.AWSCredentialsProvider

import scala.util.Try


/* Simple type to separate user-related data from the config */
case class LoquatUser(
  /* email address for notifications */
  val email: String,
  /* these are credentials that are used to launch loquat */
  val localCredentials: AWSCredentialsProvider,
  /* keypair name for connecting to the loquat instances */
  val keypairName: String
) extends Config() {

  def   deploy[L <: AnyLoquat](l: L): Unit = l.deploy(this)
  def undeploy[L <: AnyLoquat](l: L): Unit = l.undeploy(this)

  def validationErrors: Seq[String] = {
    val emailErr =
      if (email.contains('@')) Seq()
      else Seq(s"User email [${email}] has invalid format")

    emailErr ++ {
      if (Try( localCredentials.getCredentials ).isFailure)
        Seq(s"Couldn't load your local credentials: ${localCredentials}")
        // TODO: add account permissions validation
      else {
        // FIXME: this should use region:
        val ec2 = EC2.create(localCredentials)
        if(ec2.isKeyPairExists(keypairName)) Seq()
        else Seq(s"key pair: ${keypairName} doesn't exists")
      }
    }
  }
}
