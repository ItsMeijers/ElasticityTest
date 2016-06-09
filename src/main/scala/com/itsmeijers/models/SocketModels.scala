package com.itsmeijers.models

package object SocketModels {

  case class CurrentStatus(
    available: Boolean,
    completed: Boolean,
    reason: Option[String],
    totalDuration: Option[Int],
    currentState: Option[String]) // Current state can be Requesting, Aggregating, Saving

  def postiveStatus(totalDuration: Int, currentState: String) =
    CurrentStatus(true, false, None, Some(totalDuration), Some(currentState))

  def negativeStatus(reason: String) =
    CurrentStatus(false, false, Some(reason), None, None)

  def completedStatus = CurrentStatus(true, true, None, None, None)

}
