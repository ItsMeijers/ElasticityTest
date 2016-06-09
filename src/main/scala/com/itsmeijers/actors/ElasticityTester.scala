package com.itsmeijers.actors

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import com.itsmeijers.actors.ResultAggregator.AggregatingFinished
import com.itsmeijers.actors.RequestScheduler.{ScheduleStopped, TimeLeft}
import com.itsmeijers.actors.HistoryResultRetriever.HistoryResultRetrieverMessage
import com.itsmeijers.models._
import com.itsmeijers.models.ChartModels._
import com.itsmeijers.models.SocketModels._
import ElasticityTester._
import com.itsmeijers.utils.{JsonSupport, FileSaving}
import java.io.PrintWriter
import spray.json._

class ElasticityTester
  extends FSM[ElasticityState, ElasticityData]
  with ActorLogging
  with JsonSupport
  with FileSaving {

  // Setting initial state: Idle and initial data: Uninitialized
  startWith(Idle, Uninitialized(None))

  // Actor for retrieving the history of elasticityTests
  val historyRetriever = context.actorOf(HistoryResultRetriever.props, "historyResultRetriever")

  implicit val askTimeout = Timeout(5 seconds)

  def totalDuration(et: ElasticityTest): Int =
    (et.totalDuration.toString.takeWhile(_ != ' ').toInt * 60) + 60 // (for saving and aggregating!)

  // Processing messages when Idle
  when(Idle) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlyAvailable)
    case Event(UpdateSocket(testName), Uninitialized(previousTest)) =>
      val status = previousTest.flatMap { name =>
        if(name == testName) Some(completedStatus)
        else None
      } getOrElse(negativeStatus("Currently not running any tests!"))

      stay() using(Uninitialized(previousTest)) replying(SocketStatus(status))
    case Event(test: Test, Uninitialized(_)) =>
      log.debug("Received Test data switch to Requesting State")
      val et = test.elasticityTest

      // Creating the RequestScheduler Actor
      val requestScheduler =
        context.actorOf(RequestScheduler.props(et), "requestScheduler")
      // Goto the Requesting state with the TestData
      goto(Requesting) using TestData(et, requestScheduler)
  }

  // Processing messages when Requesting
  when(Requesting) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlyRequesting)
    case Event(UpdateSocket(testName), testData: TestData) =>
      val currentTest = testData.elasticityTest.name
      val response = if(testName == currentTest) {
        // Request current percentage from the RequestScheduler
        // (testData.requestScheduler ? TimeLeft)
        //   .mapTo[Double]
        //   .map(timeLeft => SocketStatus(postiveStatus(timeLeft, "Requesting")))
        SocketStatus(postiveStatus(totalDuration(testData.elasticityTest), "Requesting"))
      } else {
          SocketStatus(negativeStatus(s"Requested test is not running, currently running $currentTest"))
      }
      // use pipeTo pattern -- ugly fix
      // val response = Await.result(responseF, 4 seconds)

      stay() using(testData) replying(response)
    case Event(scheduleStopped: ScheduleStopped, testData: TestData) =>
      val et = testData.elasticityTest
      // Create the resultAggregator which starts aggregating immediatly based
      // on the elasticityTest name
      val resultAggregator =
        context.actorOf(ResultAggregator.props(et), "resultAggregator")
      // Goto the aggregating state with the elasticityTest and the resultAggregator
      goto(Aggregating) using AggregateData(et, resultAggregator)
  }

  // Processing messages when Aggregating
  when(Aggregating) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlyAggregating)
    case Event(UpdateSocket(testName), ad: AggregateData) =>
      val currentTest = ad.elasticityTest.name

      val response = if(testName == currentTest)
        SocketStatus(postiveStatus(totalDuration(ad.elasticityTest), "Aggregating"))
      else
        SocketStatus(negativeStatus(s"Currently not aggregating for $testName, but for $currentTest"))

      stay() using(ad) replying(response)
    case Event(AggregatingFinished(uriWithIntervalResults), data: AggregateData) =>
      goto(Saving) using SaveData(data.elasticityTest, uriWithIntervalResults)
  }

  when(Saving) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlySaving)
    case Event(UpdateSocket(testName), sd: SaveData) =>
      val currentTest = sd.elasticityTest.name

      val response = if (testName == currentTest)
        SocketStatus(postiveStatus(totalDuration(sd.elasticityTest), "Saving"))
      else SocketStatus(negativeStatus(s"Currently not saving for $testName, but for $currentTest"))

      stay() using(sd) replying(response)
    case Event((elasticityTestResult, saved), sd: SaveData) =>
      log.debug(s"Received saved: $elasticityTestResult with saved: $saved")
      goto(Idle) using Uninitialized(Some(sd.elasticityTest.name))
  }

  // Processing messages when they are not handled in the Requesting or Idle state
  whenUnhandled {
    case Event(retrieveMessage: HistoryResultRetrieverMessage, data) =>
      historyRetriever forward retrieveMessage
      stay()
    case Event(msg, data) =>
      log.debug(s"Unhandled message: $msg with data: $data")
      stay()
  }

  // Monitoring state changes
  onTransition {
    case Idle -> Requesting =>
      log.debug("State change from Idle -> Requesting")
    case Requesting -> Aggregating =>
      log.debug("State change from Requesting -> Aggregating")

      stateData match {
        case testData: TestData =>
          // Shutdown the requestScheduler, since its not being used anymore
          testData.requestScheduler ! PoisonPill
        case _ =>
          log.debug("Received unitalized in state transitation: Requesting -> Aggregating")
      }
    case Aggregating -> Saving =>
      log.debug("State change from Aggregating -> Saving")
      nextStateData match {
        case SaveData(elasticityTest, uriWithIntervalResults) =>
          // calculate and save the results on a different thread
          calculateAndSaveData(elasticityTest, uriWithIntervalResults)
            .pipeTo(self)
        case _ =>
          log.debug("Received wrong data in Aggregating -> Saving")
      }
    case Saving -> Idle =>
      log.debug("State change from Saving -> Idle")
  }

  def calculatePieChartData(uwi: Seq[(String, Seq[IntervalRouteResult])]) = {
    val columns = Seq(
      Column("StatusCode", "string", Some("Status Code"), None),
      Column("NumberOfResponses", "number", Some("Number of Respones"), None))

    val rows = uwi
      .flatMap(_._2) // only use the Seq[IntervalRouteResult]
      .flatMap(_.singleStatusResults) // get each singlestatus result
      .map(ssr => ssr.status -> ssr.numberOfResponses) // get the status with number of responses
      .groupBy(_._1)
      .map{case (key, value) => key -> value.map(_._2).sum}
      .foldRight(Seq[Row]()){(curr, acc) =>
        acc :+ Row(Seq(StringValue(curr._1.toString), NumberValue(curr._2)))
      }

    Data(columns, rows)
  }

  def calculateBarChartData(uwi: Seq[(String, Seq[IntervalRouteResult])]) = {
    val firstCol = Column("URI", "string", Some("URI"), None)
    val restCol = uwi
      .flatMap(_._2)
      .flatMap(_.singleStatusResults)
      .map(_.status)
      .distinct
      .map { status =>
        Column(status.toString, "number", Some(status.toString), None)
      }

    val columns = firstCol +: restCol

    def getNumberValues(status: Int, iResults: Seq[IntervalRouteResult]): NumberValue =
      NumberValue(
        iResults
          .flatMap(_.singleStatusResults)
          .filter(_.status == status)
          .map(_.numberOfResponses)
          .sum
      )

    val rows = uwi.map { case (uri, iResults) =>
      val c = columns.map {
        case Column("URI", _, _, _) => StringValue(uri)
        case col: Column => getNumberValues(col.id.toInt, iResults)
      }
      Row(c)
    }

    Data(columns, rows)
  }

  def calculateLineChartUris(uriWithIntervals: Seq[(String, Seq[IntervalRouteResult])]): Seq[UriData] = {
    val cols = Seq(
      Column("time", "number", Some("Time (minutes)"), None),
      Column("responseTime", "number", Some("Response Time (ms)"), None),
      Column("min", "number", None, Some("interval")),
      Column("max", "number", None, Some("interval")))

    uriWithIntervals.map { case (uri, intervalRouteResults) =>
      val rows = intervalRouteResults
        .sortBy(_.interval)
        .map { ir =>
          val (min, max) = ir.singleStatusResults.foldRight((0.0, 0.0)){ (ssr, acc) =>
            val newMin = if(ssr.minResponseTime > acc._1) ssr.minResponseTime
              else acc._1
            val newMax = if(ssr.maxResponseTime > acc._2) ssr.maxResponseTime
              else acc._2
            (newMin, newMax)
          }

          Row(Seq(
            NumberValue(ir.interval),
            NumberValue(ir.averageResponseTime),
            NumberValue(min),
            NumberValue(max)
          ))
        }
      UriData(cols, rows, uri)
    }
  }

  def calculateInstancesChart(uriWithIntervals: Seq[(String, Seq[IntervalRouteResult])]): Data = {
    val cols = Seq(
      Column("time", "number", Some("Time (minutes)"), None),
      Column("responseTime", "number", Some("Average Response Time (milliseconds)"), None),
      Column("serverInstances", "number", Some("Server Instances"), None)
    )

    val rows = uriWithIntervals
      .flatMap(_._2) // extract intervalRouteResults
      .groupBy(_.interval)
      .map { case (interval, routeResults) =>
        val average = routeResults.map(_.averageResponseTime).sum / routeResults.length
        Row(Seq(NumberValue(interval), NumberValue(average), NumberValue(1))) // MAKE SURE TO ADD Server Instances
      }.toSeq
      .sortBy(_.c.head match {
        case StringValue(v) => 0.0
        case NumberValue(v) => v
      })

    Data(cols, rows)
  }

  def calculateAndSaveData(
    elasticityTest: ElasticityTest,
    uriWithIntervalResults: Seq[(String, Seq[IntervalRouteResult])]): Future[(ElasticityTestResult, Boolean)] =
      Future {
        val intervalRouteResults = uriWithIntervalResults.flatMap(_._2)
        val elasticityTestResult = ElasticityTestResult(
          name = elasticityTest.name,
          totalDuration = elasticityTest.totalDuration.toString.takeWhile(_ != ' '),
          host = elasticityTest.host,
          totalResponses = intervalRouteResults.map(_.totalResponses).sum,
          averageResponseTime = 0.0, //TODO
          postiveResults = 100, // todo
          negativeResults = 10, // TODO
          routeResults = intervalRouteResults,
          pieChartData = calculatePieChartData(uriWithIntervalResults),
          barChartData = calculateBarChartData(uriWithIntervalResults),
          lineChartUris = calculateLineChartUris(uriWithIntervalResults),
          responseInstancesChart = calculateInstancesChart(uriWithIntervalResults)
        )

        val json = elasticityTestResult.toJson

        val jsonString = json.prettyPrint

        val dir = newFile(s"${elasticityTest.name}/result").mkdir()

        if(dir) {
          val jsonFile = newFile(s"${elasticityTest.name}/result/elasticityTestResult.json")
          val writer = new PrintWriter(jsonFile)
          writer.write(jsonString)
          writer.close()
          elasticityTestResult -> true
        }
        else
          elasticityTestResult -> false
      }
}

object ElasticityTester {
  def props = Props(classOf[ElasticityTester])

  sealed trait ElasticityTesterMessage
  case class Test(elasticityTest: ElasticityTest) extends ElasticityTesterMessage
  case object IsAvailable extends ElasticityTesterMessage
  case class LifecycleEvent(lifecycleHook: LifecycleHook, receiveTime: String) extends ElasticityTesterMessage

  // Messages for updating the socket
  sealed trait SocketMessage
  case class UpdateSocket(testName: String) extends SocketMessage
  case class SocketStatus(currentStatus: CurrentStatus) extends SocketMessage

  // Messages for the status when a new elasticitytest gets created
  sealed trait ElasticityTestStatus
  case object CurrentlyAggregating extends ElasticityTestStatus
  case object CurrentlySaving extends ElasticityTestStatus
  case object CurrentlyAvailable extends ElasticityTestStatus
  case object CurrentlyRequesting extends ElasticityTestStatus

  // All the possible states of the FSM
  sealed trait ElasticityState
  case object Idle extends ElasticityState
  case object Requesting extends ElasticityState
  case object Aggregating extends ElasticityState
  case object Saving extends ElasticityState

  // Data objects for testing an ElasticityTest
  sealed trait ElasticityData
  case class Uninitialized(previousTest: Option[String]) extends ElasticityData

  // For loading the testData with the the RequestScheduler ActorRef
  final case class TestData(
    elasticityTest: ElasticityTest,
    requestScheduler: ActorRef) extends ElasticityData

  // For loading the AggregateData with the ResultAggregator ActorRef
  final case class AggregateData(
    elasticityTest: ElasticityTest,
    resultAggregator: ActorRef) extends ElasticityData

  // For saving the summarized data to a json file
  final case class SaveData(
    elasticityTest: ElasticityTest,
    uriWithIntervalResults: Seq[(String, Seq[IntervalRouteResult])]) extends ElasticityData
}
