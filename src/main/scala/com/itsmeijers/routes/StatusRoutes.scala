package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorRef
import com.itsmeijers.models.SocketModels._
import com.itsmeijers.actors.ElasticityTester.{UpdateSocket, SocketStatus}
import com.itsmeijers.utils.JsonSupport
import spray.json._
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}

/**
* Add a socket that communicates with the client during testing
*/
trait StatusRoutes extends JsonSupport {

  val duration = 4 seconds

  implicit val timeout = Timeout(duration)

  def test(elasticityTester: ActorRef)(implicit materializer: ActorMaterializer) : Flow[Message, Message, Any] =
  Flow[Message].map {
    case tm: TextMessage.Strict =>
      val textMessageF = (elasticityTester ? UpdateSocket(tm.text))
        .mapTo[SocketStatus]
        .map(ss => TextMessage(ss.currentStatus.toJson.compactPrint))

      Await.result(textMessageF, duration)
    case _ =>
      TextMessage(negativeStatus("Unsupported message!").toJson.compactPrint)
  }

  def status(elasticityTester: ActorRef)(implicit materializer: ActorMaterializer) =
    pathPrefix("api" / "status") {
      pathEnd {
        get {
          complete {
            HttpResponse(200, entity = "Status: Idle or Running!")
          }
        }
      } ~ path("current") {
        println("Websocket connection requested!")
        // Websocket
         handleWebSocketMessages(test(elasticityTester))
      }
    }
}
