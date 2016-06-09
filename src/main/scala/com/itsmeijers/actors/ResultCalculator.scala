package com.itsmeijers.actors

import akka.actor._
import ResultAggregator._
import com.github.tototoshi.csv._
import com.itsmeijers.models.{IntervalRouteResult, SingleStatusResult}
import scala.collection.immutable.Seq
import com.itsmeijers.actors.ResultAggregator.FinishedFile

class ResultCalculator extends Actor with ActorLogging {

  def receive = {
    case ProcessFileFromUri(uri, file) =>
      log.debug(s"Processing ${file.getName()}")
      val reader = CSVReader.open(file)
      val csv: Seq[Seq[String]] = reader.all()

      val singleStatusResults = csv.map {
        case status :: startTime :: endTime :: Nil =>
          status.toInt -> (BigInt(endTime).toDouble - BigInt(startTime).toDouble)
      }.groupBy(_._1)
       .map { case (status, statusWithTimes) =>

         val times = statusWithTimes.map(_._2)

         val standardDeviation = {
           val length = times.length.toDouble
           val average = times.sum.toDouble / length
           Math.sqrt(times.map(t => Math.pow(t - average, 2)).sum / (length - 1))
         }

         val averageResponseTime = times.sum / times.length

         SingleStatusResult(
           status = status,
           averageResponseTime = averageResponseTime,
           maxResponseTime = averageResponseTime + standardDeviation,
           minResponseTime = averageResponseTime - standardDeviation,
           numberOfResponses = times.length
         )
       }.toVector

      val intervalRouteResult = IntervalRouteResult(
        uri = uri,
        interval = file.getName().drop(9).dropRight(4).toInt,
        totalResponses = csv.length,
        averageResponseTime = singleStatusResults.map(_.averageResponseTime).sum / singleStatusResults.length,
        singleStatusResults = singleStatusResults)

      sender() ! FinishedFile(uri, intervalRouteResult)
    case unkown => // handle unkown
  }

}

object ResultCalculator {

  def props = Props(classOf[ResultCalculator])

}
