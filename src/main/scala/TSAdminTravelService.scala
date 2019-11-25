
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


object TSAdminTravelService {
  implicit val timeout: Timeout = 2.seconds

  class AdminTravelService(travelService: ActorRef, travel2service: ActorRef) extends Actor {

    override def receive: Receive = {


      case GetAllTravels =>
        var trips1: List[Trip] = List()
        var orderResponse: Option[List[Trip]] = None
        val response: Future[Any] = travelService ? QueryAllTravel()
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) orderResponse = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Trip]])
            else orderResponse = None
          case Failure(_) =>
            orderResponse = None
        }
        orderResponse match{
          case Some(trips) =>
            trips1 = trips
          case None =>
          //Do nothing
        }
        var trips2: List[Trip] = List()
        var orderResponse2: Option[List[Trip]] = None
        val response2: Future[Any] = travel2service ? QueryAllTravel()
        response2 onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) orderResponse2= Some(res.asInstanceOf[Response].data.asInstanceOf[List[Trip]])
            else orderResponse2 = None
          case Failure(_) =>
            orderResponse = None
        }
        orderResponse2 match{
          case Some(trips) =>
            trips2 = trips
          case None =>
          //Do nothing
        }
        sender() ! Response(0, "Success", trips1 ++ trips2)

      case c:AddTravel =>
        var service: ActorRef = null
        if (c.request.tripId ==1 || c.request.tripId == 2)  service = travelService
        else  service = travel2service
        val response: Future[Any] = service ? CreateTravel(c.request)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }

      case c:UpdateTravel =>
        var service: ActorRef = null
        if (c.travelInfo.tripId ==1 || c.travelInfo.tripId == 2)  service = travelService
        else  service = travel2service
        val response: Future[Any] = service ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }

      case c:DeleteTravel =>
        var service: ActorRef = null
        if (c.tripId ==1 || c.tripId == 2)  service = travelService
        else  service = travel2service
        val response: Future[Any] = service ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }
    }
  }

}



