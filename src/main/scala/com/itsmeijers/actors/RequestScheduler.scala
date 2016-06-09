package com.itsmeijers.actors

import akka.actor._
import com.itsmeijers.models.{ElasticityTest}
import RequestScheduler._
import scala.concurrent.duration._
import scala.collection.immutable.{Queue, Seq}
import akka.http.scaladsl.model.HttpRequest
import scala.util.{Try, Success, Failure}

class RequestScheduler(val elasticityTest: ElasticityTest)
  extends FSM[RequestSchedulerState, RequestSchedulerData]
  with ActorLogging {

  override def postStop(): Unit = {
    log.debug("Stopping RequestScheduler")
  }

  def initIntervalQueue: IntervalQueue = IntervalQueue(
    elasticityTest.requestWithInterval.map {
      case (httpRequest: HttpRequest, queue: Queue[(Double, Int)]) =>
        val ((firstInterval, intervalCount), newQueue) = queue.dequeue

        val httpRequester = context.actorOf(HttpRequester.props(
          elasticityTest,
          httpRequest,
          firstInterval,
          intervalCount))

        (httpRequester, httpRequest, newQueue)
    })

  startWith(Requesting, initIntervalQueue)

  //Scheduler to send next interval messages to the actor itself
  implicit val disp = context.dispatcher
  val intervalScheduler = context.system.scheduler.schedule(
    1 minute,
    1 minute,
    self,
    NextInterval)

  // Processing messages when Requesting
  when(Requesting) {
    case Event(NextInterval, intervalQueue: IntervalQueue) =>
      nextIntervalQueue(intervalQueue) match {
        case Failure(_)   => goto(Finished) using(EmptyRequestScheduler)
        case Success(iq) => goto(Requesting) using(iq)
      }
    case Event(TimeLeft, intervalQueue: IntervalQueue) =>
      stay() replying(getTimeLeft(intervalQueue))
  }

  // Processing messages when Finished
  when(Finished) {
    case Event(TimeLeft, EmptyRequestScheduler) =>
      stay() replying(20) // 20 seconds for aggregating and saving
  }

  // Processing unhandled messages
  whenUnhandled {
    case Event(msg, data) =>
      log.debug(s"Unhandled message: $msg with data: $data")
      stay()
  }

  // Monitoring transitions mostly for shuttingdown the old httpRequesters
  onTransition {
    case Requesting -> Requesting =>
      log.debug("Switched state from Requesting -> Requesting")

      shutdownHttpRequesters(stateData)
    case Requesting -> Finished =>
      log.debug("Switched state from Requesting -> Finished")

      shutdownHttpRequesters(stateData)

      context.parent ! ScheduleStopped(intervalScheduler.cancel())
  }

  /*
  * Function that shuts down each HttpRequester from the old stateData
  */
  def shutdownHttpRequesters(stateData: RequestSchedulerData): Unit =
    stateData match {
      case IntervalQueue(httpInfo) =>
        httpInfo foreach { case (ar: ActorRef, _: HttpRequest, _: Queue[(Double, Int)]) =>
          log.debug(s"Shutting down $ar")
          ar ! PoisonPill
        }
      case EmptyRequestScheduler =>
        log.debug(s"EmptyRequestScheduler: not stopping children")
    }

  /*
  * Tries to get the new interval queue with HttpRequesters and the HttpRequest
  * Can fail when the Queue is empty, which means that the RequestScheduling is done
  */
  def nextIntervalQueue(intervalQueue: IntervalQueue): Try[IntervalQueue] =
    Try {
      IntervalQueue(intervalQueue.httpInfo.map {
        case (ar: ActorRef, hr: HttpRequest, q: Queue[(Double, Int)]) =>
          val ((interval, intervalCount), newQueue) = q.dequeue
          val newRequester = context.actorOf(HttpRequester.props(
            elasticityTest,
            hr,
            interval,
            intervalCount))

          (newRequester, hr, newQueue)
      })
    }

  /*
  * TODO
  */
  def getTimeLeft(intervalQueue: IntervalQueue): Double = {
    val total = elasticityTest.totalDuration.toString.takeWhile(_ != ' ').toDouble
    val current = intervalQueue.httpInfo.head._3.length.toDouble

    total - current
  }

}

object RequestScheduler {

  def props(elasticityTest: ElasticityTest) =
    Props(classOf[RequestScheduler], elasticityTest)

  // All the possible states for the RequestScheduler
  sealed trait RequestSchedulerState
  case object Requesting extends RequestSchedulerState
  case object Finished extends RequestSchedulerState

  // All possible data state changes
  sealed trait RequestSchedulerData
  // Data for creating the interval for each HttpRequester (ActorRef)
  final case class IntervalQueue(httpInfo: Seq[(ActorRef, HttpRequest, Queue[(Double, Int)])]) extends RequestSchedulerData
  case object EmptyRequestScheduler extends RequestSchedulerData

  // RequestScheduler messages
  sealed trait RequestSchedulerMessage
  case object TimeLeft extends RequestSchedulerMessage
  case object NextInterval extends RequestSchedulerMessage
  final case class ScheduleStopped(cancelled: Boolean) extends RequestSchedulerMessage

}
