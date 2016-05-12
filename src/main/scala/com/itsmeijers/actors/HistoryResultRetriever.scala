package com.itsmeijers.actors

import akka.actor._
import com.itsmeijers.models.{History, ElasticityTestResult}
import HistoryResultRetriever._
import com.itsmeijers.utils.{JsonSupport, FileSaving}
import spray.json._
import scala.util.Try

class HistoryResultRetriever
  extends Actor
  with ActorLogging
  with JsonSupport
  with FileSaving {

  def receive = {
    case RetrieveFullHistory =>
      val history: Seq[History] = retrieveFullHistory()

      sender() ! HistoryResult(history)
    case RetrieveResult(name) =>
      val resultOpt = retrieveElasticityTestResult(name)

      sender() ! ElasticityTestRetrieveResult(resultOpt)
    case unkown =>
      log.debug(s"Recieved unkown message in HistoryRetriever: $unkown")
  }

  def retrieveFullHistory(): Seq[History] =
    openUpHistoryFiles().map(readFile(_).parseJson.convertTo[History])

  def retrieveElasticityTestResult(name: String): Option[ElasticityTestResult] =
    Try(newFile(s"$name/result/elasticityTestResult.json")).map { file =>
      readFile(file).parseJson.convertTo[ElasticityTestResult]
    }.toOption

}

object HistoryResultRetriever {

  def props = Props(classOf[HistoryResultRetriever])

  sealed trait HistoryResultRetrieverMessage

  case object RetrieveFullHistory extends HistoryResultRetrieverMessage
  case class HistoryResult(history: Seq[History]) extends HistoryResultRetrieverMessage
  case class RetrieveResult(elasticityTestName: String) extends HistoryResultRetrieverMessage
  case class ElasticityTestRetrieveResult(elasticityTestResultOpt: Option[ElasticityTestResult]) extends HistoryResultRetrieverMessage
}
