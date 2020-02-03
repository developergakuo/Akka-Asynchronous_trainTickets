import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.{Failure, Success}
object TSAdminOrderService {

  class AdminOrderService(orderService: ActorRef, orderOtherService: ActorRef) extends Actor {


    override def receive: Receive = {
      case c:GetAllOrders =>
        println("Sending orders")
        var orders1: List[Order] = List()
        var orderResponse: Option[List[Order]] = None
        val responseFuture: Future[Any] = orderService ? c
        val response = Await.result(responseFuture,duration).asInstanceOf[Response]
        if (response.status == 0) orderResponse = Some(response.data.asInstanceOf[List[Order]])
            orderResponse = None

        orderResponse match{
          case Some(orders) =>
            orders1 = orders
          case None =>
            //Do nothing
        }
        var orders2: List[Order] = List()
        var orderResponse2: Option[List[Order]] = None
        val response2Future: Future[Any] = orderOtherService ? c
        val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
        if (response2.status == 0) orderResponse2= Some(response2.data.asInstanceOf[List[Order]])
        orderResponse2 match{
          case Some(orders) =>
            orders2 = orders
          case None =>
          //Do nothing
        }
        println("Sending orders")
        sender() ! Response(0, "Success", orders1 ++ orders2)

/*
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
        val response: Future[Any] = service ? CreateOrder(c.request)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0, "Success", None)
            else sender() ! Response(1, "Error", None)
          case Failure(_) =>
            sender() ! Response(1, "Error", None)
        }
*/

    }
  }

}



