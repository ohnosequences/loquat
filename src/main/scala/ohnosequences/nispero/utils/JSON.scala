package ohnosequences.nispero.utils

// import net.liftweb.json._
// import net.liftweb.json.JsonAST.JValue
// import net.liftweb.json.JsonAST.JString
// import net.liftweb.json.TypeInfo
// import net.liftweb.json.JsonParser.ParseException
import ohnosequences.nispero._
import ohnosequences.awstools.ec2.InstanceType
import ohnosequences.awstools.autoscaling._
import java.io.File
import ohnosequences.nispero.utils.pickles._
import upickle._

object pickles {


  implicit val instanceTypeWriter = upickle.Writer[InstanceType]{
    case it => Js.Str(it.toString)
  }

  implicit val instanceTypeReader = upickle.Reader[InstanceType]{
    case Js.Str(str) => InstanceType.fromName(str)
  }

  implicit val purchaseModelWriter = upickle.Writer[PurchaseModel] {
    case OnDemand => Js.Arr(Js.Str("OnDemand"), Js.Obj())
    case SpotAuto => Js.Arr(Js.Str("SpotAuto"), Js.Obj())
    case Spot(price) => Js.Arr(Js.Str("Spot"), Js.Obj(("price", Js.Num(price))))
  }

  implicit val tasksProviderWriter = upickle.Writer[TasksProvider] {
    case _ => Js.Str("SomeTaskProvider")
  }
  // implicit val taskWriter = upickle.Writer[Task]{
  //   case it => Js.Str(it.toString)
  // }

  // implicit val taskReader = upickle.Reader[Task]{
  //   case Js.Str(str) => InstanceType.fromName(str)
  // }

  // def toJson[X](x: X): String = upickle.write(x)

  // def parse[T](s: String): T = {
  //   val json = JsonParser.parse(s)

  //   implicit val formats = net.liftweb.json.DefaultFormats + InstanceTypeSerializer
  //   json.extract[T]
  // }

  // def parseFromResource[T](name: String)(implicit mf: scala.reflect.Manifest[T]): Option[T] = {
  //   val r = getClass.getResourceAsStream(name)
  //   if (r == null) {
  //     println("couldn't fount resource: " + name)
  //     None
  //   } else {
  //     val s = scala.io.Source.fromInputStream(r).mkString
  //     try {
  //       Some(parse[T](s))
  //     } catch {
  //       case t: ParseException => {
  //         println("error during parsing initial tasks")
  //         t.printStackTrace()
  //         None
  //       }
  //     }
  //   }
  // }

}
