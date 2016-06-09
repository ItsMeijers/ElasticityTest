package com.itsmeijers.routes

import com.itsmeijers.utils.JsonSupport
import com.itsmeijers.actors.ElasticityTester.LifecycleEvent
import com.itsmeijers.models.LifecycleHook
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import spray.json._

trait LifecycleHookRoutes extends JsonSupport {

  def lifecycleHooks(elasticityTester: ActorRef) = pathPrefix("api" / "lifecycle") {
    pathEnd {
      (post & entity(as[LifecycleHook])) { lifecycleHook =>
        val timeReceived = System.currentTimeMillis().toString
        complete {
          elasticityTester ! LifecycleEvent(lifecycleHook, timeReceived)
          HttpResponse(200)
        }
      }
    }
  }

}
