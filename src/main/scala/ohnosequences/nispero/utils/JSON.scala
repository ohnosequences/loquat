package ohnosequences.nispero.utils

import ohnosequences.nispero._
import ohnosequences.awstools.ec2.InstanceType
import ohnosequences.awstools.autoscaling._
import java.io.File
import upickle._, default._

object pickles {


  implicit val instanceTypeWriter: Writer[InstanceType] = Writer[InstanceType]{
    case it => Js.Str(it.toString)
  }

  implicit val instanceTypeReader: Reader[InstanceType] = Reader[InstanceType]{
    case Js.Str(str) => InstanceType.fromName(str)
  }

  implicit val purchaseModelWriter: Writer[PurchaseModel] = Writer[PurchaseModel] {
    case OnDemand => Js.Arr(Js.Str("OnDemand"), Js.Obj())
    case SpotAuto => Js.Arr(Js.Str("SpotAuto"), Js.Obj())
    case Spot(price) => Js.Arr(Js.Str("Spot"), Js.Obj(("price", Js.Num(price))))
  }

  // FIXME: this is not really needed and is wrongly used somewhere in the console
  implicit val tasksProviderWriter: Writer[TasksProvider] = Writer[TasksProvider] {
    case _ => Js.Str("SomeTaskProvider")
  }

}
