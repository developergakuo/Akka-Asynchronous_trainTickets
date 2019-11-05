


import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

implicit val timeout: Timeout = 2.seconds

object TSAdminOrderService {

  class AdminOrderService extends Actor {
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null

    override def receive: Receive = {
      case c:GetAllOrders =>
        var orders1: List[Order] = List()
        var orderResponse: Option[List[Order]] = None
        val response: Future[Any] = orderService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) orderResponse = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Order]])
            else orderResponse = None
          case Failure(_) =>
            orderResponse = None
        }
        orderResponse match{
          case Some(orders) =>
            orders1 = orders
          case None =>
            //Do nothing
        }
        var orders2: List[Order] = List()
        var orderResponse2: Option[List[Order]] = None
        val response2: Future[Any] = orderOtherService ? c
        response2 onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) orderResponse2= Some(res.asInstanceOf[Response].data.asInstanceOf[List[Order]])
            else orderResponse2 = None
          case Failure(_) =>
            orderResponse = None
        }
        orderResponse2 match{
          case Some(orders) =>
            orders2 = orders
          case None =>
          //Do nothing
        }
        sender() ! Response(0, "Success", orders1 ++ orders2)

      case c:DeleteOrder2 =>
        var service: ActorRef = null
        if (c.trainNumber ==1 || c.trainNumber == 2)  service = orderService
        else  service = orderOtherService
        val response: Future[Any] = service ? DeleteOrder(c.orderId)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }
      case c:UpdateOrder2 =>
        var service: ActorRef = null
        if (c.request.trainNumber ==1 || c.request.trainNumber == 2)  service = orderService
        else  service = orderOtherService
        val response: Future[Any] = service ? SaveChanges(c.request)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }
      case c:AddOrder =>
        var service: ActorRef = null
        if (c.request.trainNumber ==1 || c.request.trainNumber == 2)  service = orderService
        else  service = orderOtherService
        val response: Future[Any] = service ? Create(c.request)
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



