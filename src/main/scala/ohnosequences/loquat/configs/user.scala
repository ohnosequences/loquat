package ohnosequences.loquat

import ohnosequences.awstools.ec2._
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
) extends Config("User config")() {

  def    check[L <: AnyLoquat](l: L): Unit = l.check(this)
  def   deploy[L <: AnyLoquat](l: L): Unit = l.deploy(this)
  def undeploy[L <: AnyLoquat](l: L): Unit = l.undeploy(this)

  def validationErrors(aws: AWSClients): Seq[String] = {

    val emailErr =
      if (email.contains('@')) Seq()
      else Seq(s"User email [${email}] has invalid format")

    emailErr ++ {
      if(aws.ec2.isKeyPairExists(keypairName)) Seq()
      else Seq(s"key pair: ${keypairName} doesn't exists")
    }
  }

}
