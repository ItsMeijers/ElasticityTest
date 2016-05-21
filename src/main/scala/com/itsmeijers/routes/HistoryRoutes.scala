package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.itsmeijers.models.History
import scala.collection.immutable.Seq
import com.itsmeijers.actors.HistoryResultRetriever.{HistoryResult, RetrieveFullHistory}
import akka.actor.ActorRef
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import com.itsmeijers.utils._
import spray.json._

trait HistoryRoutes extends JsonSupport with FileSaving {

  private implicit val timeout = Timeout(4 seconds)

  def history(elasticityTester: ActorRef) = pathPrefix("api" / "history") {
    pathEnd {
      complete {
        (elasticityTester ? RetrieveFullHistory) // gets forwarded to HistoryRetriever
          .mapTo[HistoryResult]
          .map { historyResult =>
            historyResult.history
          }
      }
    }
  }
}
