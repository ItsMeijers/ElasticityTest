package com.itsmeijers.actors

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.itsmeijers.actors.ResultAggregator.AggregatingFinished
import com.itsmeijers.actors.RequestScheduler.{ScheduleStopped, CurrentPercentage}
import com.itsmeijers.actors.HistoryResultRetriever.HistoryResultRetrieverMessage
import com.itsmeijers.models._
import com.itsmeijers.models.ChartModels._
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
  startWith(Idle, Uninitialized)

  // Actor for interpreting the DSL
  //val interpreter = context.actorOf(Interpreter.props, "interpreter")

  // Actor for retrieving the history of elasticityTests
  val historyRetriever = context.actorOf(HistoryResultRetriever.props, "historyResultRetriever")

  implicit val askTimeout = Timeout(5 seconds)

  // Processing messages when Idle
  when(Idle) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlyAvailable)
    case Event(test: Test, Uninitialized) =>
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
    case Event(UpdateSocket, testData: TestData) =>
      // Request current percentage from the RequestScheduler
      val testPercentage = (testData.requestScheduler ? CurrentPercentage)
          .mapTo[Double]
          .map(TestPercentage.apply)

      stay() replying(testPercentage)
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
    case Event(UpdateSocket, data) =>
      stay() using(data) replying(CurrentlyAggregating)
    case Event(AggregatingFinished(uriWithIntervalResults), data: AggregateData) =>
      goto(Saving) using SaveData(data.elasticityTest, uriWithIntervalResults)
  }

  when(Saving) {
    case Event(IsAvailable, data) =>
      stay() using(data) replying(CurrentlySaving)
    case Event(UpdateSocket, data) =>
      stay() using(data) replying(CurrentlySaving)
    case Event((elasticityTestResult, saved), data) =>
      log.debug(s"Received saved: $elasticityTestResult with saved: $saved")
      goto(Idle) using Uninitialized
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
        acc :+ Row(Seq(StringValue(curr._1.toString), IntValue(curr._2)))
      }

    Data(columns, rows)
  }

  // {"cols" : [
  //   {id:'URI', label:'URI', type: 'string'},
  //   {id:'OK', label:'OK', type: 'number'},
  //   {id:'BadRequest', label:'BadRequest', type: 'number'},
  //   {id:'NotFound', label:'NotFound', type: 'number'}
  // ], "rows": [
  //   {c: [{v: "/load?cpu=30"}, {v: 1000}, {v: 400}, {v: 200}]},
  //   {c: [{v: "/load?cpu=50"}, {v: 1170}, {v: 460}, {v: 250}]},
  //   {c: [{v: "/load?cpu=70"}, {v: 660}, {v: 1120}, {v: 300}]}
  // ]};

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

    def getIntValues(status: Int, iResults: Seq[IntervalRouteResult]): IntValue =
      IntValue(
        iResults
          .flatMap(_.singleStatusResults)
          .filter(_.status == status)
          .map(_.numberOfResponses)
          .sum
      )

    val rows = uwi.map { case (uri, iResults) =>
      val c = columns.map {
        case Column("URI", _, _, _) => StringValue(uri)
        case col: Column => getIntValues(col.id.toInt, iResults)
      }
      Row(c)
    }


    Data(columns, rows)
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
          barChartData = calculateBarChartData(uriWithIntervalResults)
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
        } else elasticityTestResult -> false

      }

}

object ElasticityTester {
  def props = Props(classOf[ElasticityTester])

  sealed trait ElasticityTesterMessage
  case class Test(elasticityTest: ElasticityTest) extends ElasticityTesterMessage
  case object IsAvailable extends ElasticityTesterMessage
  case object UpdateSocket extends ElasticityTesterMessage
  final case class TestPercentage(percentage: Double) extends ElasticityTesterMessage

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
  case object Uninitialized extends ElasticityData

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
