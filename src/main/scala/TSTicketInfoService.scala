import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import akka.pattern.ask


implicit val timeout: Timeout = 2.seconds
import scala.util.{Success, Failure}


object TSTicketInfoService {
  class TicketInfoService extends Actor {
    var basicService: ActorRef = null

    override def receive: Receive = {
      //request for goods from stockActor
      case c:QueryForTravel =>
        val response: Future[Any] = basicService ? c.travel
        response onComplete{
          case Success(resp)=>
            if(resp.asInstanceOf[Response].status == 0)
              sender() ! Response(0,"Success",resp.asInstanceOf[Response].data.asInstanceOf[TravelResult])
            else sender() ! Response(1,"failed",resp.asInstanceOf[Response].data)
          case Failure(t) =>
            sender() ! Response(1,"failed",t)
        }
      case c:QueryForStationId =>
        val response: Future[Any] = basicService ? c
        response onComplete{
          case Success(resp)=>
            if(resp.asInstanceOf[Response].status == 0)
              sender() ! Response(0,"Success",resp.asInstanceOf[Response].data.asInstanceOf[Int])
            else sender() ! Response(1,"failed",resp.asInstanceOf[Response].data)
          case Failure(t) =>
            sender() ! Response(1,"failed",t)
        }
    }

  }


}
