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
  requestWithInterval: Seq[(HttpRequest, Queue[(Double, Int)])])
