package ohnosequences.nisperito

case object tasks {

  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.cosas._, typeSets._, properties._, records._
  import java.io.File

  sealed trait AnyTask {

    val id: Int

    type InputObj
    val inputObjects: Map[String, InputObj]

    val outputObjects: Map[String, ObjectAddress]
  }

  case class TinyTask(
    val id: Int,
    val inputObjects: Map[String, String],
    val outputObjects: Map[String, ObjectAddress]
  ) extends AnyTask { type InputObj = String }

  case class BigTask(
    val id: Int,
    val inputObjects: Map[String, ObjectAddress],
    val outputObjects: Map[String, ObjectAddress]
  ) extends AnyTask { type InputObj = ObjectAddress }



  case class TaskResultDescription(
    id: Int,
    message: String,
    instanceId: Option[String],
    time: Int
  )


  trait AnyKey extends AnyProperty {
    type Raw = File
    lazy val label: String = this.toString

    val prefix: String
    def file: File = new File(s"${prefix}/${label}")
  }
  // class Key[R](val prefix: String) extends AnyKey { type Raw = R }

  trait InputKey extends AnyKey { val prefix = "input" }
  trait OutputKey extends AnyKey { val prefix = "output" }

  type InputKeys = AnyTypeSet.Of[InputKey]
  type OutputKeys = AnyTypeSet.Of[OutputKey]

  // type OutputFiles = AnyRecord { type Properties <: AnyTypeSet.of[TaskOutput] }
  type FilesFor[Ks <: AnyTypeSet.Of[AnyKey]] = AnyRecord.withProperties[Ks]


}
