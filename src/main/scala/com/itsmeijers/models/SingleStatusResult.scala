package com.itsmeijers.models

/**
* Results from an interval for a single status
**/
case class SingleStatusResult(
  status: Int,
  averageResponseTime: Double,
  maxResponseTime: Double,
  minResponseTime: Double,
  numberOfResponses: Int)
