


import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


object TSAdminRouteService {


  class AdminRouteService(routeService: ActorRef ) extends Actor {




    override def receive: Receive = {
      case c:GetAllRoutes =>
        var routes: Option[List[Route]] = None
        val response: Future[Any] = routeService ? GetAllRoutes()
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) routes = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Route]])
            else routes = None
          case Failure(_) =>
            routes = None
        }
        routes match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(1,"Error",None)
        }

      case c:CreateAndModifyRoute =>
        val response: Future[Any] = routeService ? CreateAndModify(c.RouteInfo)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:DeleteRoute =>
        val response: Future[Any] = routeService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

    }
  }

}



