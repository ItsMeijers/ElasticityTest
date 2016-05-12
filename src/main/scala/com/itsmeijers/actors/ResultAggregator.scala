package com.itsmeijers.actors

import akka.actor._
import com.itsmeijers.models.{ElasticityTest, IntervalRouteResult}
import java.io.File
import akka.routing.{ ActorRefRoutee, RoundRobinRoutingLogic, Router }
import ResultAggregator._
import scala.collection._
import com.itsmeijers.utils.FileSaving

/**
* Actor for aggregating all the results for an ElasticityTest
* The actor reads each csv file and routes it to the ResultCalculators
* Result is a Seq of Uri (String) -> Seq of interval results
**/
class ResultAggregator(elasticityTest: ElasticityTest)
  extends Actor
  with ActorLogging
  with FileSaving {

  // Open up each directory that represents a single uri
  // Each uri linked to a sequene of files which hold each interval results
  val uriWithFiles: Seq[(String, Seq[File])] =
    newFile(elasticityTest.name)
      .listFiles()
      .filter(_.isDirectory())
      .map { folder =>
        // uri (replace placeholder) -> to each file in the folder
        folder.getName().replaceAll("<>", "/")  -> folder.listFiles().toSeq
      }.toSeq

  log.debug(s"Created uri with files: $uriWithFiles")

  // Retrieve total amount of files for checking wether processing is done
  val amountOfFiles = uriWithFiles.map(_._2.length).sum

  // Router thats able to distribute the workload over all the available
  // processors in a round robin fashion
  val router = {
    val routees = Vector.fill(Runtime.getRuntime().availableProcessors()) {
      val r = context.actorOf(ResultCalculator.props)
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  // Route each file with their uri for processing
  uriWithFiles.foreach { case (uri: String, files: Seq[File]) =>
    files.foreach { file =>
      log.debug(s"Routing file: ${file.getPath()}")
      router.route(ProcessFileFromUri(uri, file), self)
    }
  }

  // Mutable Map for aggregating each result from processing
  val intervalRouteResults: mutable.Map[String, Seq[IntervalRouteResult]] =
    mutable.Map(uriWithFiles.map { case (uri: String, _: Seq[File]) =>
      uri -> mutable.Seq()
    }: _*)

  def getIntervalRouteResultsLength: Int =
    intervalRouteResults.values.map(_.length).sum

  def receive = {
    case FinishedFile(uri, intervalRouteResult) =>
      // update map with the new result
      log.debug(s"Updating map with uri: $uri and result $intervalRouteResult")
      val intervalResults = intervalRouteResults(uri)
      intervalRouteResults(uri) = intervalResults :+ intervalRouteResult

      // Check wether aggregating is finished
      if(getIntervalRouteResultsLength == amountOfFiles) {
        log.debug(s"Aggregating finished")

        context.parent ! AggregatingFinished(intervalRouteResults.toSeq)
        // if Aggregating is finished insert a poisenpill to the actor itself
        self ! PoisonPill
      }
    case unkown => // handle unkown
  }

  override def postStop(): Unit = {
    log.debug("Stopping resultAggregator")
  }
}

object ResultAggregator {
  def props(elasticityTest: ElasticityTest) =
    Props(classOf[ResultAggregator], elasticityTest)

  sealed trait ResultAggregatorMessage
  case class AggregatingFinished(uriWithIntervalResults: Seq[(String, Seq[IntervalRouteResult])]) extends ResultAggregatorMessage
  case object AggregatingError extends ResultAggregatorMessage
  case class FinishedFile(uri: String, intervalRouteResult: IntervalRouteResult) extends ResultAggregatorMessage
  case class ProcessFileFromUri(uri: String, file: File) extends ResultAggregatorMessage

}
