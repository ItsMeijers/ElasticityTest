package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

/**
* Add a socket that communicates with the client during testing
*/
trait StatusRoutes {

  def test(implicit materializer: ActorMaterializer) : Flow[Message, Message, Any] =
  Flow[Message].map {
    case tm: TextMessage.Strict =>
      TextMessage(s"Echo: ${tm.text}")
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
      TextMessage("Unsupported message!")
  }

  //def updater: ActorRef

  def status(implicit materializer: ActorMaterializer) = pathPrefix("api" / "status") {
    pathEnd {
      get {
        complete {
          HttpResponse(200, entity = "Status: Idle or Running!")
        }
      }
    } ~ path("current") {
      println("Websocket connection requested!")
      // Websocket
       handleWebSocketMessages(test)
    }
  }
}
