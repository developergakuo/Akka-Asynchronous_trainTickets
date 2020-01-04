import TSCommon.Commons._
import akka.actor. ActorRef

import java.util.{Calendar, Date}

import akka.persistence._


object TSCancelService {

  case class CancelOderState(requests: Map[(ActorRef, Int), CancelRequest])

  case class RefundState(requests: Map[(ActorRef, Int), RefundRequest])

  case class CancelRequest(var accountId: Int = -1, var order: Option[Order] = None, var account: Option[Account] = None,
                           var isDrawnBack: Boolean = false, var isCancled: Boolean = false,var refundAmount: Double = 0)

  case class RefundRequest(var order: Option[Order] = None, var refundAmount: Double = 0)

  case class CancelServiceSate(cancelRequests: CancelOderState, refundRequests: RefundState)

  class CancelService(orderService: ActorRef, orderOtherService: ActorRef, travelService: ActorRef,
                      travel2service: ActorRef, stationService: ActorRef, insidePayService: ActorRef,
                      seatService: ActorRef, userService: ActorRef, notificationService: ActorRef) extends PersistentActor with AtLeastOnceDelivery {

    var state: CancelServiceSate = CancelServiceSate(CancelOderState(Map()), RefundState(Map()))

    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "CancelService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: CancelServiceSate) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case e: CancelOrder =>
        e.requester ! CancelOrderDelivered(e.deliveryId)
        state = CancelServiceSate(CancelOderState(state.cancelRequests.requests + ((e.requester, e.requestId) -> CancelRequest(accountId = e.accountId))),
          state.refundRequests)
      case e: ResponseFindOrderById =>
        confirmDelivery(e.deliveryId)
        if(e.found) {
          if(e.requestLabel.equals("CancelOrder")){
            state.cancelRequests.requests.get((e.requester,e.requestId)).get.order=Some(e.order)
            if (e.order.status == OrderStatus().NOTPAID._1
              || e.order.status == OrderStatus().PAID._1 || e.order.status == OrderStatus().CHANGE._1) {
              e.order.status = OrderStatus().CANCEL._1
              if (e.sourceLabel.equals("Order")) cancelFromOrder(e.order,e.requester,e.requestId)
              else if (e.sourceLabel.equals("OtherOrder")) cancelFromOtherOrder(e.order,e.requester,e.requestId)
            }
          }
          else if(e.requestLabel.equals("CalculateRefund")){
              if (e.order.status == OrderStatus().NOTPAID._1 || e.order.status == OrderStatus().PAID._1) {
                if (e.order.status == OrderStatus().NOTPAID._1)
                  sender() ! Response(0, "Success. Refund error 0", 0)
                else
                  e.requester ! Response(0, "Success. ", calculateRefund(e.order))
              } else sender() ! Response(1, "Order Status Cancel Not Permitted, Refund error", null)

          }

          }
      case e: ResponseCancelOrder =>
        confirmDelivery(e.deliveryId)
        state.cancelRequests.requests.get((e.requester,e.requestId)).get.isCancled=e.canceled
        val money = calculateRefund(state.cancelRequests.requests.get((e.requester,e.requestId)).get.order.get)
        state.cancelRequests.requests.get((e.requester,e.requestId)).get.refundAmount=money
        drawbackMoney(money,state.cancelRequests.requests.get((e.requester,e.requestId)).get.accountId,e.requester,e.requestId)

      case e: ResponseDrawBack =>
        confirmDelivery(e.deliveryId)
        state.cancelRequests.requests.get((e.requester,e.requestId)).get.isDrawnBack=e.drawnBack
        asyncGetAccount(state.cancelRequests.requests.get((e.requester,e.requestId)).get.accountId,e.requester,e.requestId)
      case e: ResponseFindByUserId2 =>
        confirmDelivery(e.deliverId)
        e.account match {
          case Some(account) =>
            state.cancelRequests.requests.get((e.requester,e.requestId)).get.account= e.account
            val user = account
            val order= state.cancelRequests.requests.get((e.requester,e.requestId)).get.order.get
            val notifyInfo = NotifyInfo(user.email, order.id, user.userName, order.from, order.to, order.travelTime, new Date(), order.seatClass, order.seatNumber, order.price)
            deliver(notificationService.path)(deliveryId=> Order_cancel_success(notifyInfo,e.requester,deliveryId,e.requestId))
          case None =>
            e.requester ! Response(1, "Cancel error, user could not be found", None)

        }
      case e:RequestComplete =>
        confirmDelivery(e.deliveryId)
    }

    override def receiveCommand: Receive = {
      case c: CancelOrder =>
        persist(c)(updateState)
        asyncGetOrderByIdFromOrder(c.orderId, "CancelOrder", c.requester, c.requestId)
        AsyncGetOrderByIdFromOrderOther(c.orderId, "CancelOrder", c.requester, c.requestId)

      case c: ResponseFindOrderById =>
        persist(c)(updateState)

      case c: ResponseCancelOrder =>
        persist(c)(updateState)

      case c: ResponseDrawBack =>
        persist(c)(updateState)

      case c: ResponseFindByUserId2 =>
        persist(c)(updateState)

      case c: RequestComplete =>
        persist(c)(updateState)

      case c: CalculateRefund =>
        asyncGetOrderByIdFromOrder(c.orderId, "CalculateRefund", c.requester, c.requestId)
        AsyncGetOrderByIdFromOrderOther(c.orderId, "CalculateRefund", c.requester, c.requestId)

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


        def cancelFromOrder(order: Order,requester: ActorRef, requestId: Int): Unit = {
         deliver(orderService.path)(deliveryId => CancelOrder(order.accountId, order.id,deliveryId,requester,requestId))
        }

        def cancelFromOtherOrder(order: Order,requester: ActorRef, requestId: Int ): Unit = {
          deliver(orderOtherService.path)(deliveryId => CancelOrder(order.accountId, order.id,deliveryId,requester,requestId))

        }

        def drawbackMoney(money: Double, userId: Int,requester: ActorRef, requestId: Int): Unit = {

        deliver(insidePayService.path)(deliveryId =>  DrawBack(userId, money,deliveryId,requester,requestId))

        }

        def asyncGetAccount(accountId: Int,requester: ActorRef, requestId: Int): Unit = {

         deliver(userService.path) (deliveryId=> FindByUserId2(deliveryId,requester, requestId, accountId))

        }


        def asyncGetOrderByIdFromOrder(orderId: Int, requestLabel: String, requester: ActorRef, requestId: Int ): Unit = {
          deliver(orderService.path)(deliveryId =>FindOrderById(orderId,deliveryId,requester,requestId,requestLabel,"Order"))
        }

        def AsyncGetOrderByIdFromOrderOther(orderId: Int, requestLabel: String, requester: ActorRef, requestId: Int ): Unit = {
          deliver(orderOtherService.path)(deliveryId =>FindOrderById(orderId,deliveryId,requester,requestId,requestLabel,"OtherOrder"))

        }
  }
}







