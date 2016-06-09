package com.itsmeijers.utils

import cats.data.Validated
import cats.data.Validated.{ valid, invalid }
import cats.syntax.apply._
import cats.std.list._
import cats.syntax.cartesian._
import scala.concurrent.duration._
import org.joda.time.DateTime
import scala.collection.immutable.{Queue, Seq}
import akka.http.scaladsl.model.{HttpRequest, HttpMethod}
import akka.http.scaladsl.model.HttpMethods
import com.itsmeijers.models._
import scala.util.Try

object ElasticityTestValidation {

  type Error = List[String]
  type EValidated[A] = Validated[Error, A]

  type URIS = Seq[(HttpRequest, Queue[(Double, Int)])]

  def invalidE[A](msg: String): EValidated[A] = invalid(List(msg))

  def toElasticityTest(etf: ElasticityTestForm): EValidated[ElasticityTest] =
    (validateName(etf.testName) |@|
    valid(new DateTime().toString("MM/dd/yyyy HH:mm")) |@|
    validateDuration(etf.totalDuration) |@|
    validateHost(etf.host) |@|
    validatePort(etf.port) |@|
    validateUris(etf.uris, etf.totalDuration)).map(ElasticityTest.apply _)

  def validateName(name: String): EValidated[String] =
    if(name.isEmpty) invalidE("Test name can't be empty.")
    else valid(name)

  def validateDuration(duration: Int): EValidated[FiniteDuration] =
    if(duration > 0) valid(duration minutes)
    else invalidE("Total duration has to be longer than 0 minutes.")

  def validateHost(host: String): EValidated[String] =
    if(host.isEmpty) invalidE("Host name can't me empty.")
    else if(host.startsWith("http://")) invalidE("Host can't start with 'http://'")
    else valid(host)

  def validatePort(port: Option[Int]): EValidated[Option[Int]] = port map {p =>
    if(p > 0) valid(port)
    else invalidE("Port can't be lower than 0.")
  } getOrElse(valid(None))

  def validateUris(uris: Seq[Uri], totalDuration: Int): EValidated[URIS] = {
    val isValidDuration = uris
      .map(_.interval)
      .forall { i =>
        Try{
          i.split(',').forall(int => Try(int.toInt).isSuccess)
        }.isSuccess
      }
    if(isValidDuration) {
      valid {
        uris.map { uri =>
          val method: HttpMethod = uri.method match {
            case "GET" => HttpMethods.GET
            case "POST" => HttpMethods.POST
            case "DELETE" => HttpMethods.DELETE
            case "PUT" => HttpMethods.PUT
          }
          val httpRequest = HttpRequest(method = method, uri = uri.uri) // add body
          val queue = createQueue(uri.interval.split(',').map(_.toInt).toVector, totalDuration)

          httpRequest -> queue
        }
      }
    } else {
      invalidE("Interval on URIs need to be an comma seperated list of integers")
    }
  }

  def createQueue(interval: Seq[Int], totalDuration: Int): Queue[(Double, Int)] = {
    val intervalStepSize = totalDuration / (interval.length - 1)

    if(interval.length == 1){
      val intervalStep = List.fill(totalDuration)(interval.head.toDouble).zipWithIndex
      Queue(intervalStep: _*)
    }
    else {
      val stepSizes: Seq[Double] = interval
        .zip(interval.tail)
        .map { case (start, end) =>
          (end.toDouble - start.toDouble) / intervalStepSize.toDouble
        }

      val intervalList = interval.zip(stepSizes).flatMap { case (i, step) =>
        List.fill(intervalStepSize)(i)
          .zipWithIndex
          .map { case (int, index) => int + (index * step) }
      }

      val finalList = (intervalList :+ (stepSizes.last + intervalList.last)).zipWithIndex

      Queue(finalList: _*)
    }
  }

}
