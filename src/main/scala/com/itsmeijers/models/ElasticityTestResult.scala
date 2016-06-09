package com.itsmeijers.models

import com.itsmeijers.models.ChartModels.Data
import com.itsmeijers.models.ChartModels.UriData

case class ElasticityTestResult(
  name: String,
  totalDuration: String,
  host: String,
  totalResponses: Int,
  averageResponseTime: Double,
  postiveResults: Int,
  negativeResults: Int,
  routeResults: Seq[IntervalRouteResult],
  pieChartData: Data,
  barChartData: Data,
  lineChartUris: Seq[UriData],
  responseInstancesChart: Data)
