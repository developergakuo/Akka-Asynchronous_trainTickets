
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await

object TSExecuteService {

  class ExecuteService(orderService: ActorRef, orderOtherService: ActorRef ) extends Actor {
    override def receive: Receive = {
      case TicketExecute(orderId: Int) =>
        getOrderByIdFromOrder(orderId) match{
          case Some(order) =>
            if (order.status == OrderStatus().COLLECTED._1)
              sender() ! Response(1, "Order Status Wrong", null)
            else{
              if (executeOrder(orderId, OrderStatus().USED._1))
                sender() !  Response(0, "Success.", null)
              else sender() !  Response(1, "Error", null)
            }
          case None =>
            getOrderByIdFromOrderOther(orderId) match {
              case Some(order) =>
                if (order.status == OrderStatus().COLLECTED._1)
                  sender() !  Response(1, "Order Status Wrong", null)
                 else{
                  if (executeOrderOther(orderId, OrderStatus().USED._1))
                    sender() ! Response(0, "Success", null)
                  else sender() ! Response(1, "Error", null)
                }
              case None =>
                sender() ! Response(1, "Order Not Found", null)
            }
        }

      case TicketCollect(orderId: Int)=>
         getOrderByIdFromOrder(orderId) match {
           case Some(order) =>
             if ((order.status == OrderStatus().PAID._1) && (order.status == OrderStatus().CHANGE._1))
               sender() ! Response(1, "Order Status Wrong", null)
             else{
               if (executeOrder(orderId, OrderStatus().COLLECTED._1))
                 sender() ! Response(0, "Success", null)
               else sender() ! Response(1, "Error", null)
             }
           case None =>
             getOrderByIdFromOrderOther(orderId) match {
               case Some(order) =>
                 if ((order.status == OrderStatus().PAID._1) && (order.status == OrderStatus().CHANGE._1))
                   sender() ! Response(1, "Order Status Wrong", null)
                 else{
                   if (executeOrderOther(orderId, OrderStatus().COLLECTED._1))
                     sender() ! Response(0, "Success.", null)
                   else sender() ! Response(1, "Error", null)
                 }
               case None =>
                 sender() ! Response(1, "Order Not Found", null)
             }
         }
    }


     def executeOrder(orderId: Int, status: Int): Boolean = {
      var is_modified: Boolean = false
      val responseFuture: Future[Any] = orderService ? ModifyOrder(orderId,status)
       val response = Await.result(responseFuture,duration).asInstanceOf[Response]
     if (response.status == 0) is_modified = true
      is_modified
    }


    def executeOrderOther(orderId: Int, status: Int):Boolean = {
      var is_modified: Boolean = false
      val responseFuture: Future[Any] = orderOtherService ? ModifyOrder(orderId,status)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) is_modified = true
      is_modified
    }


     def getOrderByIdFromOrder(orderId: Int): Option[Order] = {
      var order: Option[Order] = None
      val responseFuture: Future[Any] = orderService ? GetOrderById(orderId)
       val response = Await.result(responseFuture,duration).asInstanceOf[Response]
       if (response.status == 0) order = Some(response.data.asInstanceOf[Order])
      order
    }

     def getOrderByIdFromOrderOther(orderId: Int):Option[Order] = {
       var order: Option[Order] = None
       val responseFuture: Future[Any] = orderOtherService ? GetOrderById(orderId)
       val response = Await.result(responseFuture,duration).asInstanceOf[Response]
       if (response.status == 0) order = Some(response.data.asInstanceOf[Order])
       order
    }
  }
}