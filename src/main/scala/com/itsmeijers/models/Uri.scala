package com.itsmeijers.models

case class Uri(
  uri: String,
  interval: String,
  method: String,
  body: Option[String])
