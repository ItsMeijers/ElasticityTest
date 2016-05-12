package com.itsmeijers.models

case class ElasticityTestResult(
  name: String,
  totalDuration: String,
  host: String,
  totalResponses: Int,
  averageResponseTime: Double,
  postiveResults: Int,
  negativeResults: Int,
  routeResults: Seq[IntervalRouteResult])
