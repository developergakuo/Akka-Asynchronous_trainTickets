import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import java.util.{Calendar, Date}

implicit val timeout: Timeout = 2.seconds

object TSCancelService {

  class CancelService extends Actor {
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null
    var travelService: ActorRef = null
    var travel2service: ActorRef = null
    var stationService: ActorRef = null
    var insidePayService: ActorRef = null
    var seatService: ActorRef = null
    var userService: ActorRef = null
    var notificationService: ActorRef = null

    override def receive: Receive = {
      case c: CancelOrder =>
        val orderResult = getOrderByIdFromOrder(c.orderId)
        orderResult match {
          case Some(order) =>
            if (order.status == OrderStatus().NOTPAID._1
              || order.status == OrderStatus().PAID._1 || order.status == OrderStatus().CHANGE._1) {

              order.status = OrderStatus().CANCEL._1

              val changeOrderResult = cancelFromOrder(order)
              // 0 -- not find order   1 - cancel success
              if (changeOrderResult) {

                //Draw back money
                val money = calculateRefund(order)
                val status = drawbackMoney(money, c.accountId)
                if (status) {
                  // todo
                  val accountResult = getAccount(order.accountId)
                  accountResult match {
                    case Some(user) =>
                      val notifyInfo = NotifyInfo(user.email, c.orderId, user.userName, order.from, order.to, order.travelTime, new Date(), order.seatClass, order.seatNumber, order.price)
                      sendEmail(notifyInfo);
                    case None =>
                      sender ! Response(1, "Cann't find userinfo by user id.", null)
                  }
                }
                sender() ! Response(0, "Success.", null)
              } else {
                sender() ! Response(1, "Error in order cancellation", null)
              }

            } else {
              sender() ! Response(0, "Order Status Cancel Not Permitted", null)
            }
          case None =>
            val orderOtherResult = getOrderByIdFromOrderOther(c.orderId)
            orderOtherResult match {
              case Some(order) =>

                if (order.status == OrderStatus().NOTPAID._1
                  || order.status == OrderStatus().PAID._1 || order.status == OrderStatus().CHANGE._1) {

                  order.status = OrderStatus().CANCEL._1

                  val changeOrderResult = cancelFromOrder(order)
                  // 0 -- not find order   1 - cancel success
                  if (changeOrderResult) {

                    //Draw back money
                    val money = calculateRefund(order)
                    val status = drawbackMoney(money, c.accountId)
                    if (status) {
                      // todo
                      val accountResult = getAccount(order.accountId)
                      accountResult match {
                        case Some(user) =>
                          val notifyInfo = NotifyInfo(user.email, c.orderId, user.userName, order.from, order.to, order.travelTime, new Date(), order.seatClass, order.seatNumber, order.price)
                          sendEmail(notifyInfo);
                        case None =>
                          sender ! Response(1, "Cann't find userinfo by user id.", null)
                      }
                    }
                    sender() ! Response(0, "Success.", null)
                  } else {
                    sender() ! Response(1, "Error in order cancellation", null)
                  }

                } else {
                  sender() ! Response(0, "Order Status Cancel Not Permitted", null)
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
                      sender() ! Response(0, "Order Status Cancel Not Permitted, Refound error", null);

                  case None =>
                    val orderResult2: Option[Order] = getOrderByIdFromOrderOther(c.orderId)
                    orderResult2 match {
                      case Some(order2) =>
                        if (order2.status == OrderStatus().NOTPAID._1 || order2.status == OrderStatus().PAID._1) {
                          if (order2.status == OrderStatus().NOTPAID._1)
                            sender() ! Response(0, "Success. Refund error 0", 0)
                          else
                            sender() ! Response(0, "Success. ", calculateRefund(order2))
                        } else sender() ! Response(0, "Order Status Cancel Not Permitted, Refund error", null);

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
          val response: Future[Any] = orderService ? CancelOrder(order.accountId, order.id)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(true)
              else result = Some(false)
            case Failure(_) =>
              result = None
          }
          result.get
        }

        def cancelFromOtherOrder(order: Order): Boolean = {
          var result: Option[Boolean] = None
          val response: Future[Any] = orderOtherService ? CancelOrder(order.accountId, order.id)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(true)
              else result = Some(false)
            case Failure(_) =>
              result = None
          }
          result.get

        }

        def drawbackMoney(money: Double, userId: Int): Boolean = {
          var result: Option[Boolean] = None
          val response: Future[Any] = insidePayService ? DrawBack(userId, money)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Boolean])
              else result = None
            case Failure(_) =>
              result = None
          }
          result.get
        }

        def getAccount(accountId: Int): Option[UserDto] = {
          var result: Option[UserDto] = None
          val response: Future[Any] = userService ? FindByUserId2(accountId)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[UserDto])
              else result = None
            case Failure(_) =>
              result = None
          }
          result
        }


        def getOrderByIdFromOrder(orderId: Int): Option[Order] = {
          var result: Option[Order] = None
          val response: Future[Any] = orderService ? FindOrderById(orderId)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Order])
              else result = None
            case Failure(_) =>
              result = None
          }
          result
        }


        def getOrderByIdFromOrderOther(orderId: Int): Option[Order] = {
          var result: Option[Order] = None
          val response: Future[Any] = orderOtherService ? FindOrderById(orderId)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Order])
              else result = None
            case Failure(_) =>
              result = None
          }
          result
        }

        def sendEmail(notifyInfo: NotifyInfo): Boolean = {
          var result: Option[Boolean] = None
          val response: Future[Any] = notificationService ? SendNotification(notifyInfo)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) result = Some(true)
              else result = None
            case Failure(_) =>
              result = None
          }
          result.get
        }

  }
}







