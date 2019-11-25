import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}


object TSCancelService {

  class CancelService(orderService: ActorRef , orderOtherService: ActorRef , travelService: ActorRef ,
                      travel2service: ActorRef , stationService: ActorRef , insidePayService: ActorRef ,
                      seatService: ActorRef , userService: ActorRef , notificationService: ActorRef ) extends Actor {

    override def receive: Receive = {
      case c: CancelOrder =>
        val orderResult = getOrderByIdFromOrder(c.orderId)
        orderResult match {
          case Some(order) =>
            if (order.status == OrderStatus().NOTPAID._1
              || order.status == OrderStatus().PAID._1 || order.status == OrderStatus().CHANGE._1) {

              order.status = OrderStatus().CANCEL._1
              // 0 -- not find order   1 - cancel success
              if (cancelFromOrder(order)) {
                //Draw back money
                val money = calculateRefund(order)
                if (drawbackMoney(money, c.accountId)) {
                  // todo
                  val accountResult = getAccount(order.accountId)
                  accountResult match {
                    case Some(user) =>
                      val notifyInfo = NotifyInfo(user.email, c.orderId, user.userName, order.from, order.to, order.travelTime, new Date(), order.seatClass, order.seatNumber, order.price)
                      notificationService ! Order_cancel_success(notifyInfo,sender())
                    case None =>
                      sender ! Response(1, "Can't find userinfo by user id.", null)
                  }
                } else sender() ! Response(1, "Error: DrawBack Error.", null)
              } else {
                sender() ! Response(1, "Error in order cancellation", null)
              }

            } else {
              sender() ! Response(1, "Order Status Cancel Not Permitted", null)
            }
          case None =>
            val orderOtherResult = getOrderByIdFromOrderOther(c.orderId)
            orderOtherResult match {
              case Some(order) =>
                if (order.status == OrderStatus().NOTPAID._1
                  || order.status == OrderStatus().PAID._1 || order.status == OrderStatus().CHANGE._1) {

                  order.status = OrderStatus().CANCEL._1
                  if (cancelFromOrder(order)) {
                    //Draw back money
                    val money = calculateRefund(order)
                    if (drawbackMoney(money, c.accountId)) {
                      // todo
                      val accountResult = getAccount(order.accountId)
                      accountResult match {
                        case Some(user) =>
                          val notifyInfo = NotifyInfo(user.email, c.orderId, user.userName, order.from, order.to, order.travelTime, new Date(), order.seatClass, order.seatNumber, order.price)
                          notificationService ! Order_cancel_success(notifyInfo,sender())
                        case None =>
                          sender ! Response(1, "Cann't find userinfo by user id.", null)
                      }
                    }
                    else sender() ! Response(1, "Error: Drawback Error", null)
                  }
                  else sender() ! Response(1, "Error in order cancellation", null)


                } else {
                  sender() ! Response(1, "Order Status Cancel Not Permitted", null)
                }
              case None =>
                sender() ! Response(1, "Order Not Found", null)
            }
        }

      case c: CalculateRefund =>
                val orderResult: Option[Order] = getOrderByIdFromOrder(c.orderId)
                orderResult match {
                  case Some(order) =>
                    if (order.status == OrderStatus().NOTPAID._1 || order.status == OrderStatus().PAID._1) {
                      if (order.status == OrderStatus().NOTPAID._1)
                        sender() ! Response(0, "Success. Refoud 0", 0)
                      else
                        sender() ! Response(0, "Success. ", calculateRefund(order))
                    } else
                      sender() ! Response(1, "Order Status Cancel Not Permitted, Refound error", null);
                  case None =>
                    val orderResult2: Option[Order] = getOrderByIdFromOrderOther(c.orderId)
                    orderResult2 match {
                      case Some(order2) =>
                        if (order2.status == OrderStatus().NOTPAID._1 || order2.status == OrderStatus().PAID._1) {
                          if (order2.status == OrderStatus().NOTPAID._1)
                            sender() ! Response(0, "Success. Refund error 0", 0)
                          else
                            sender() ! Response(0, "Success. ", calculateRefund(order2))
                        } else sender() ! Response(1, "Order Status Cancel Not Permitted, Refund error", null);
                      case None =>
                        sender() ! Response(1, "Order Not Found", null)
                    }
                }
            }
        def calculateRefund(order: Order): Double = {
          var result = -1.0
          if (order.status == OrderStatus().NOTPAID._1) {
            result = 0.00
          }
          val nowDate = new Date()
          val cal = Calendar.getInstance()
          cal.setTime(order.travelDate)
          val year = cal.get(Calendar.YEAR)
          val month = cal.get(Calendar.MONTH)
          val day = cal.get(Calendar.DAY_OF_MONTH)
          val cal2 = Calendar.getInstance()
          cal2.setTime(order.travelTime)
          val hour = cal2.get(Calendar.HOUR)
          val minute = cal2.get(Calendar.MINUTE)
          val second = cal2.get(Calendar.SECOND)
          val startTime = new Date(year,
            month,
            day,
            hour,
            minute,
            second)

          if (nowDate.after(startTime)) result = 0.00
          else {
            val totalPrice = order.price
            result = totalPrice * 0.8
          }
          result
        }


        def cancelFromOrder(order: Order): Boolean = {
          var result: Option[Boolean] = None
          val responseFuture: Future[Any] = orderService ? CancelOrder(order.accountId, order.id)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) result = Some(true)
          result.get
        }

        def cancelFromOtherOrder(order: Order): Boolean = {
          var result: Option[Boolean] = None
          val responseFuture: Future[Any] = orderOtherService ? CancelOrder(order.accountId, order.id)
                val response = Await.result(responseFuture,duration).asInstanceOf[Response]
              if (response.status == 0) result = Some(true)

          result.get

        }

        def drawbackMoney(money: Double, userId: Int): Boolean = {
          var result: Option[Boolean] = None
          val responseFuture: Future[Any] = insidePayService ? DrawBack(userId, money)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) result = Some(true)
          result.get
        }

        def getAccount(accountId: Int): Option[Account] = {
          var result: Option[Account] = None
          val responseFuture: Future[Any] = userService ? FindByUserId2(accountId)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) result = Some(response.data.asInstanceOf[Account])
          result
        }


        def getOrderByIdFromOrder(orderId: Int): Option[Order] = {
          var result: Option[Order] = None
          val responseFuture: Future[Any] = orderService ? FindOrderById(orderId)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) result = Some(response.data.asInstanceOf[Order])
          result
        }


        def getOrderByIdFromOrderOther(orderId: Int): Option[Order] = {
          var result: Option[Order] = None
          val responseFuture: Future[Any] = orderOtherService ? FindOrderById(orderId)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) result = Some(response.data.asInstanceOf[Order])
          result
        }
  }
}







