package com.itsmeijers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.itsmeijers.routes._
import com.itsmeijers.actors._
import com.itsmeijers.utils.{FileSaving, CorsSupport}

object ApplicationMain
  extends App
  with IndexRoutes
  with DSLRoutes
  with StatusRoutes
  with ResultsRoutes
  with HistoryRoutes
  with FileSaving
  with CorsSupport {

  implicit val system = ActorSystem("ElasticityTest")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val et = system.actorOf(ElasticityTester.props, "elasticityTester")

  // All the routes which are in defined individually in each corresponding trait
  val route = cors {
    index ~ dsl(et) ~ status ~ results(et) ~ history(et)
  }

  val foldersCreated = createTestFolder() && createResultFolder()

  if(foldersCreated){
    val binding = Http().bindAndHandle(handler = route, "localhost", port = 8080)
    binding onFailure {
      case ex: Exception =>
        println("Could not bind on 8080!\n Reason: ")
    }
    sys.addShutdownHook(system.terminate())
  } else {
    println("TestFolder could not be created!")
  }


}
