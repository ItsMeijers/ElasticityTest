package com.itsmeijers.actors

import akka.actor._
import akka.actor.Status._
import com.github.tototoshi.csv._
import HttpRequester.HttpResult
import com.itsmeijers.utils.FileSaving

class Persister(
  val testName: String,
  val uri: String,
  val intervalCount: Int) extends Actor with ActorLogging with FileSaving {

  // Create file and open it with the CSV Writer on creation
  val file = newFile(s"$testName/$uri/interval-$intervalCount.csv")
  val writer = CSVWriter.open(file)

  // Close file when stopping actor
  override def postStop(): Unit = writer.close()

  def receive = {
    case HttpResult(uri, startTime, endTime, httpResponse) =>
      // Write RequestResult as a row to the result csv file
      writer.writeRow(List(httpResponse.status.intValue.toString, startTime, endTime))
    case Failure(cause) =>
      // persist failure as a result, counts too
      // TODO
      log.debug(s"Failure $cause")
    case unknown =>
      // handle unkown
      log.debug(s"Unknown message received: $unknown")
  }

}

object Persister {

  def props(testName: String, uri: String, intervalCount: Int) =
    Props(classOf[Persister], testName, uri, intervalCount)

}
