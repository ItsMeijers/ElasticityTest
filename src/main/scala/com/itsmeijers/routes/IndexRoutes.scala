package com.itsmeijers.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

trait IndexRoutes {
  val index = path("api"){
    get {
      complete {
        HttpResponse(200, entity = "Index!")
      }
    }
  }
}
