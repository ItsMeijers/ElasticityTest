package com.itsmeijers.utils

import com.itsmeijers.models._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

// Trait for all the json formatting
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  // Json support for SingleStatusResult
  implicit val singleStatusResultFormat = jsonFormat5(SingleStatusResult)
  // Json support for IntervalRouteResult
  implicit val intervalRouteResultFormat = jsonFormat5(IntervalRouteResult)
  // Json support for ElasticityTestResult
  implicit val elasticityTestResultFormat = jsonFormat8(ElasticityTestResult)
  // Json support for History
  implicit val historyFormat = jsonFormat5(History)

}
