package com.itsmeijers.utils

import com.itsmeijers.models._
import com.itsmeijers.models.ChartModels._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

// Trait for all the json formatting
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  // Json support for CharModels
  implicit val columnChartFormat = jsonFormat4(Column)
  implicit val stringValueChartFormat = jsonFormat1(StringValue)
  implicit val intValueChartFormat = jsonFormat1(IntValue)
  implicit object RowValueFormat extends RootJsonFormat[RowValue] {
    def write(r: RowValue) = r match {
      case s: StringValue => s.toJson
      case i: IntValue => i.toJson
    }

    def read(value: JsValue) = value.asJsObject.fields("v") match {
      case JsNumber(_) => value.convertTo[IntValue]
      case JsString(_) => value.convertTo[StringValue]
    }
  }
  implicit val rowChartFormat = jsonFormat1(Row)
  implicit val dataChartFormat = jsonFormat2(Data)
  implicit val graphDataChartFormat = jsonFormat3(GraphData)

  // Json support for SingleStatusResult
  implicit val singleStatusResultFormat = jsonFormat5(SingleStatusResult)
  // Json support for IntervalRouteResult
  implicit val intervalRouteResultFormat = jsonFormat5(IntervalRouteResult)
  // Json support for ElasticityTestResult
  implicit val elasticityTestResultFormat = jsonFormat10(ElasticityTestResult)
  // Json support for History
  implicit val historyFormat = jsonFormat5(History)
  // Json support for UriWithInterval
  implicit val uriWithIntervalFormat = jsonFormat4(Uri)
  // Json support for ElasticityTestForm
  implicit val elasticityTestFormFormat = jsonFormat5(ElasticityTestForm)

}
