package ohnosequences.nispero.bundles.console

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.nispero.bundles._
import org.clapper.avsl.Logger
import ohnosequences.nispero.bundles.console.pojo.{FarmStatePojo, FarmState}
import ohnosequences.nispero.{Names, InstanceTags}
import java.util.Date
import java.text.SimpleDateFormat
import ohnosequences.awstools.ec2.TagFilter
import ohnosequences.awstools.ec2.RequestStateFilter
import ohnosequences.awstools.ec2.InstanceStateFilter
import ohnosequences.awstools.ec2.Tag
import com.amazonaws.services.dynamodbv2.datamodeling._, DynamoDBMapperConfig._
import com.amazonaws.services.dynamodbv2.model._
import scala.collection.JavaConversions._


abstract class FarmStateLogger(resourcesBundle: Resources, aws: AWS) extends Bundle(resourcesBundle, aws) {

  val logger = Logger(this.getClass)

  val TIMEOUT = 30000

  val format: SimpleDateFormat = new SimpleDateFormat("HH:mm:ss")

  def formatDate(date: Date) = {
    format.format(date)
  }

  def getFarmState: FarmState = {

    val groupFilter = TagFilter(Tag(InstanceTags.AUTO_SCALING_GROUP, resourcesBundle.resources.workersGroup.name))

    val installing = aws.clients.ec2.listInstancesByFilters(
      groupFilter,
      TagFilter(InstanceTags.INSTALLING),
      InstanceStateFilter("running")
    ).size

    val idle = aws.clients.ec2.listInstancesByFilters(
      groupFilter,
      TagFilter(InstanceTags.IDLE),
      InstanceStateFilter("running")
    ).size

    val processing = aws.clients.ec2.listInstancesByFilters(
      groupFilter,
      TagFilter(InstanceTags.PROCESSING),
      InstanceStateFilter("running")
    ).size

    FarmState(
      date = formatDate(new Date()),
      timestamp = new Date().getTime,
      idleInstances = idle,
      processingInstances = processing,
      installingInstances = installing
    )
  }

  def getFarmHistory: List[FarmState] = {

    //5 hours
    val interval = 1000 * 60L * 60 * 5

    val currentTimestamp = System.currentTimeMillis()
    val fromTimestamp = currentTimestamp - interval

    // FIXME: this is weird:
    val query = new DynamoDBQueryExpression[FarmStatePojo]()
      .withHashKeyValues(new FarmStatePojo) //new AttributeValue().withN(1.toString)) // Names.Tables.WORKERS_STATE_HASH_KEY_VALUE,
      .withRangeKeyCondition(
        Names.Tables.WORKERS_STATE_RANGE_KEY.getAttributeName,
        new Condition()
          .withComparisonOperator(ComparisonOperator.BETWEEN)
          .withAttributeValueList(
            new AttributeValue().withN(fromTimestamp.toString),
            new AttributeValue().withN(currentTimestamp.toString)
          )
      )

    val mapperConfig = new DynamoDBMapperConfig(new TableNameOverride(resourcesBundle.resources.workersStateTable))

    aws.dynamoMapper.query(
      classOf[FarmStatePojo],
      query,
      mapperConfig
    ).toList.map {
      pojo => FarmState.fromPojo(pojo)
    }
  }

  object FarmStateLoggerThread extends Thread("FarmStateLoggerThread") {
    override def run() {
      while (true) {
        val farmState = getFarmState
        logger.info(farmState)
        try {
          aws.dynamoMapper.save(
            farmState.toPojo,
            new DynamoDBMapperConfig(new TableNameOverride(resourcesBundle.resources.workersStateTable))
          )
        } catch {
          case t: Throwable => logger.error("couldn't save farm state: " + t)
        }
        Thread.sleep(TIMEOUT)

      }
    }
  }

  def install: Results = {
    FarmStateLoggerThread.start()
    success("FarmStateLoggerAux finished")
  }

}
