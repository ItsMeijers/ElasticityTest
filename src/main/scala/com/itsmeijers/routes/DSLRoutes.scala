package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import java.io.PrintWriter
import cats.data.{Xor, Validated, NonEmptyList => NEL}
import cats.data.Xor.{Left, Right}
import com.itsmeijers.models._
import com.itsmeijers.routes.DSLRoutes._
import com.itsmeijers.actors.ElasticityTester._
import com.itsmeijers.utils._
import scala.collection.immutable.{Queue, Seq}
import spray.json._

trait DSLRoutes extends JsonSupport with FileSaving {

  private implicit val timeout = Timeout(10 seconds)

  def dsl(elasticityTester: ActorRef) = pathPrefix("api" / "dsl") {
    pathEnd {
      // Creating a DSL which results in running the test
      (post & entity(as[ElasticityTestForm])) { etForm =>
          complete {
            (elasticityTester ? IsAvailable).mapTo[ElasticityTestStatus].map {
              case CurrentlyAvailable =>
              formToElasticityTest(etForm).fold(
                errors => HttpResponse(400, entity = errors.mkString(" , ")),
                elasticityTest => {
                  elasticityTester ! Test(elasticityTest)
                  HttpResponse(201, entity = elasticityTest.name)
                }
              )
              case _ => HttpResponse(400, entity = "Already running an Elasticity Test")
            } recover {
              case xs: Exception => HttpResponse(400, entity = xs.getMessage)
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

  def formToElasticityTest(etf: ElasticityTestForm) =
    ElasticityTestValidation.toElasticityTest(etf).map { x =>
      createFolders(x)
      x
    }


  def createFolders(elasticityTest: ElasticityTest): Xor[String, Unit] = {
      val folder = newFile(elasticityTest.name)
      val dir = folder.mkdir()

      if(dir) {
        // create the elasticityTest file
        val etFile = newFile(s"${elasticityTest.name}/${elasticityTest.name}.et")
        val writer = new PrintWriter(etFile)
        writer.write(elasticityTest.toString())
        writer.close()

        // create the folders for each uri
        val dirs = elasticityTest.requestWithInterval.map {
          case (request: HttpRequest, _: Queue[(Double, Int)]) =>
            val uriFolderName = request.uri.toString.replaceAll("/", "<>")
            val routeFolder = newFile(s"${elasticityTest.name}/$uriFolderName")
            routeFolder.mkdir()
        }

        if (dirs.contains(false)) {
          Left("Directories could not be created")
        }
        else {
          createHistoryFile(elasticityTest, "Running")
          Right(())
        }
      }
      else {
        Left("Name for this test is already taken.")
      }
  }

  def createHistoryFile(elasticityTest: ElasticityTest, status: String): Unit = {
    val historyFile = newHistoryFileJson(elasticityTest.name)
    val historyWriter = new PrintWriter(historyFile)

    val historyJson = History(
      elasticityTest.name,
      elasticityTest.requestWithInterval.length,
      elasticityTest.createdOn,
      elasticityTest.createdOn,
      status).toJson

    historyWriter.write(historyJson.prettyPrint)
    historyWriter.close()
  }
}

object DSLRoutes {
  type ErrorMessage = String // Change to InterpreterException
  type InterpreterResult = Xor[ErrorMessage, ElasticityTest]
}
