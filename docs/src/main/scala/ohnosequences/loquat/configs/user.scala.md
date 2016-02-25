
```scala
package ohnosequences.loquat

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.ec2.EC2

import com.amazonaws.auth.AWSCredentialsProvider

import scala.util.Try
```

Simple type to separate user-related data from the config

```scala
case class LoquatUser(
```

email address for notifications

```scala
  val email: String,
```

these are credentials that are used to launch loquat

```scala
  val localCredentials: AWSCredentialsProvider,
```

keypair name for connecting to the loquat instances

```scala
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

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../../test/scala/ohnosequences/loquat/test/md5.scala.md