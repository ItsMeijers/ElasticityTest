package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import cats.data.Xor
import cats.data.Xor.{Left, Right}
import com.itsmeijers.models.ElasticityTest
import com.itsmeijers.actors.Interpreter
import com.itsmeijers.routes.DSLRoutes._
import com.itsmeijers.actors.Interpreter.{InterpretResult, Result, NoResult}
import com.itsmeijers.actors.ElasticityTester.Test

trait DSLRoutes {

  private implicit val timeout = Timeout(10 seconds)

  def dsl(elasticityTester: ActorRef) = pathPrefix("api" / "dsl") {
    pathEnd {
      // Creating a DSL which results in running the test
      post {
        complete {
          println("Received Request in DSL Routes")
          //val dsl = ??? // Retrieve from body
          (elasticityTester ? Interpreter.Interpret("body"))
            .mapTo[InterpretResult].map {
              case NoResult(reason) =>
                // Return bad request if there is currently a test running
                HttpResponse(400, entity = reason)
              case Result(Left(msg)) =>
                // Return bad request if there is a interpreterError
                HttpResponse(400, entity = msg)
              case Result(Right(elasticityTest)) =>
                // Start ElasticityTest
                elasticityTester ! Test(elasticityTest)
                // Return created with the elasticityTest object as body
                HttpResponse(201, entity = s"Elasticity Test created: ${elasticityTest.name}")
            }
        }
      }
    } ~ path(Segment) { testName =>
      // Retrieving a DSL from a testName
      get {
        complete {
          HttpResponse(200, entity = s"DSL for: $testName!") // TODO
        }
      }
    }
  }
}

object DSLRoutes {
  type ErrorMessage = String // Change to InterpreterException
  type InterpreterResult = Xor[ErrorMessage, ElasticityTest]
}
