package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import com.itsmeijers.actors.HistoryResultRetriever.{RetrieveResult, ElasticityTestRetrieveResult}
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import com.itsmeijers.utils.JsonSupport

trait ResultsRoutes extends JsonSupport {

  private implicit val timeout = Timeout(4 seconds)

  def results(elasticityTester: ActorRef) =
    path("api" / "result" / Segment) { testName =>
      get {
        complete{
          (elasticityTester ? RetrieveResult(testName))
            .mapTo[ElasticityTestRetrieveResult]
            .map { result =>
              result.elasticityTestResultOpt.map { etResult =>
                Marshal(200 -> etResult).to[HttpResponse]
              }.getOrElse {
                Marshal(404 -> "ElasticityTest Not found!").to[HttpResponse]
              }
            }
        }
      }
    }
}
