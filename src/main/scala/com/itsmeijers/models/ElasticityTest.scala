package com.itsmeijers.models

import scala.concurrent.duration._
import akka.http.scaladsl.model.HttpRequest
import scala.collection.immutable.{Queue, Seq}
import akka.http.scaladsl.model.HttpMethods._
import org.joda.time.DateTime

case class ElasticityTest(
  name: String,
  createdOn: String,
  totalDuration: FiniteDuration,
  host: String,
  port: Option[Int],
  requestWithInterval: Seq[(HttpRequest, Queue[(Int, Int)])])

object ElasticityTest {

  val sampleTest = ElasticityTest(
    "TwoRequests",
    new DateTime().toString("MM/dd/yyyy HH:mm"),
    2 minutes,
    "localhost",
    Some(9000),
    Seq(
      HttpRequest(method = GET, uri = "/api") -> Queue(100 -> 0, 1 -> 1),
      HttpRequest(method = GET, uri = "/api/test") -> Queue(20 -> 0, 100 -> 1))
  )

}
