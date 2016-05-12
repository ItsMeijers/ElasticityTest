package com.itsmeijers.actors

import akka.actor._

class Updater extends Actor with ActorLogging {
  def receive = {
    case unkown => // handle unkown
  }
}

object Updater {
  def props = Props(classOf[Updater])

  sealed trait UpdaterMessage

}
