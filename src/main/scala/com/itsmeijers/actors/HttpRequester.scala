package com.itsmeijers.actors

import akka.actor._
import scala.concurrent.duration._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.stream.scaladsl._
import akka.pattern.pipe
import scala.concurrent._
import HttpRequester._
import com.itsmeijers.models.ElasticityTest

class HttpRequester(
  val elasticityTest: ElasticityTest,
  val httpRequest: HttpRequest,
  val interval: Double,
  val intervalCounter: Int)
    extends Actor
    with ActorLogging {

  import context.dispatcher

  val persister: ActorRef = context.actorOf(Persister.props(
    elasticityTest.name,
    httpRequest.uri.toString.replaceAll("/", "<>"),
    intervalCounter))

  final implicit val materializer =
    ActorMaterializer(ActorMaterializerSettings(context.system))

  val httpScheduler = context.system.scheduler.schedule(
    0 seconds,
    calculateRequestsPerSecond,
    self,
    httpRequest)

  def calculateRequestsPerSecond: FiniteDuration = {
    val requestsPerSecond = (1.0 / interval * 1000.0).milliseconds
    log.debug(s"Created interval $interval")
    requestsPerSecond
  }

  override def postStop(): Unit = {
    log.debug(s"STOPPING HTTPREQUESTER ${self.path}")
    val cancelled = httpScheduler.cancel()
    log.debug(s"HTTP REQUESTER SCHEDULER cancelled: $cancelled")
  }

  def receive = {
    case request: HttpRequest =>
      // Make a single httprequest from the scheduler and pipe the result to the persister
      val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
        Http(context.system).outgoingConnection(
          host = elasticityTest.host,
          port = elasticityTest.port.getOrElse(80))

      val startTime = System.currentTimeMillis().toString
      val responseFuture = Source.single(request)
        .via(connectionFlow)
        .runWith(Sink.head)
        .map(HttpResult(
          request.uri.toString,
          startTime,
          System.currentTimeMillis().toString,
          _))

      responseFuture pipeTo persister
  }

}

object HttpRequester {
  def props(
    elasticityTest: ElasticityTest,
    httpRequest: HttpRequest,
    interval: Double,
    intervalCounter: Int) =
      Props(classOf[HttpRequester], elasticityTest, httpRequest, interval, intervalCounter)

  sealed trait HttpRequesterMessage
  case class HttpResult(uri: String, startTime: String, endTime: String, httpResponse: HttpResponse)

}
