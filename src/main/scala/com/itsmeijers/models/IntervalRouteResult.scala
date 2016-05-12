package com.itsmeijers.models

import scala.collection.immutable.Seq

/**
* Data class for representing a single interval (1 minute) results
* uri: String representation of the uri that was requested during the interval
* totalResponses: number of responses made in the 1 minute interval
* singleStatusResults: a sequence of results for each individual http response status
**/
case class IntervalRouteResult(
  uri: String,
  interval: Int,
  totalResponses: Int,
  averageResponseTime: Double,
  singleStatusResults: Seq[SingleStatusResult])
