package com.itsmeijers.actors

import akka.actor._
import Interpreter._
import com.itsmeijers.models.{ElasticityTest, History}
import cats.data.Xor.{Left, Right}
import com.itsmeijers.routes.DSLRoutes.InterpreterResult
import com.itsmeijers.utils.{FileSaving, JsonSupport}
import java.io.PrintWriter
import akka.http.scaladsl.model.HttpRequest
import scala.collection.immutable.Queue
import spray.json._

class Interpreter
  extends Actor
  with ActorLogging
  with FileSaving
  with JsonSupport {

  def receive = {
    case Interpret(input) =>
      log.debug(s"Receceived: $input")
      val elasticityTest = ElasticityTest.sampleTest
      val folder = newFile(elasticityTest.name)
      val dir = folder.mkdir()

      if(dir) {
        // create the elasticityTest file
        val etFile = newFile(s"${elasticityTest.name}/${elasticityTest.name}.et")
        val writer = new PrintWriter(etFile)
        writer.write(input)
        writer.close()

        // create the folders for each uri
        val dirs = elasticityTest.requestWithInterval.map {
          case (request: HttpRequest, _: Queue[(Int, Int)]) =>
            val uriFolderName = request.uri.toString.replaceAll("/", "<>")
            val routeFolder = newFile(s"${elasticityTest.name}/$uriFolderName")
            routeFolder.mkdir()
        }

        if (dirs.contains(false)) {
          createHistoryFile(elasticityTest, "Failed")
          sender() ! Result(Left("Directories could not be created"))
        }
        else {
          createHistoryFile(elasticityTest, "Running")
          sender() ! Result(Right(elasticityTest))
        }
      }
      else {
        sender() ! Result(Left("Name for this test is already taken."))
      }
    case unkown => // handle unkown
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

object Interpreter {
  // Props constructor for Interpreter
  def props = Props(classOf[Interpreter])

  sealed trait InterpreterMessage
  case class Interpret(input: String) extends InterpreterMessage

  sealed trait InterpretResult
  case class Result(interpretResult: InterpreterResult) extends InterpretResult
  // Used in ElasticityTest when busy testing or aggregating
  case class NoResult(reason: String) extends InterpretResult

}
