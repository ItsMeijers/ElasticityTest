package com.itsmeijers.models

import scala.collection.immutable.Seq

case class ElasticityTestForm(
  testName: String,
  totalDuration: Int,
  host: String,
  port: Option[Int],
  uris: Seq[Uri])
