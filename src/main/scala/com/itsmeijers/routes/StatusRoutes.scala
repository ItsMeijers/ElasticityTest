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
  Flow[Message].mapConcat {
    case tm: TextMessage ⇒
      TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
    case bm: BinaryMessage ⇒
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
      Nil
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
      // Websocket
       handleWebSocketMessages(test)
    }
  }
}
