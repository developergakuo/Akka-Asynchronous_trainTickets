import java.util.Random

import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence._

import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.{Await, Future}
import InputData.moneys

object TSInsidePaymentService {

  case class Repository(payments: Map[Int, Payment2], moneys: Map[(Int, Long),Money2], onGoingRequests: Map[(ActorRef, Int), RequestInfo])

  case class RequestInfo(price: Double, tripId: Int, orderId:Int , userId: Int, var order: Order = null, var payment: Payment2 = null)

  class InsidePaymentService(paymentService: ActorRef,
                             orderService: ActorRef, orderOtherService: ActorRef,notificationService: ActorRef) extends PersistentActor with AtLeastOnceDelivery{
    var state: Repository = Repository(Map(), moneys.zipWithIndex.map(a=>(a._2+1, 0.toLong )-> a._1).toMap, Map())



    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "InsidePaymentService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: Repository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: SavePayment ⇒ state = Repository(state.payments + (c.payment.orderId -> c.payment),state.moneys, state.onGoingRequests)
      case c: CreateAccount =>
        state = Repository(state.payments, state.moneys + ((c.info.userId,0.toLong) -> Money2(c.info.userId,c.info.money)),state.onGoingRequests)
      case c:AddMoney =>
        state = Repository(state.payments, state.moneys + ((c.userId,c.deliveryId) -> Money2(c.userId,c.money)),state.onGoingRequests)
        sender() ! MoneyReceived(c.deliveryId)
      case c:DrawBack =>
        state = Repository(state.payments, state.moneys + ((c.userId,c.deliveryId) -> Money2(c.userId,c.money,MoneyType().D)),state.onGoingRequests)

      case c:Pay =>
        state = Repository(state.payments,state.moneys, state.onGoingRequests + ((c.requester,c.requestId) -> RequestInfo(c.info.price,c.info.tripId,c.info.orderId,c.info.userId)))
        
    }

    override def receiveCommand: Receive = {
      case  c:Pay =>
        val requestId = scala.util.Random.nextInt()
        c.requester = sender()
        c.requestId = requestId
        
        persist(c)(updateState)
        sender ! PaymentDelivered(c.deliverID)
        var service: ActorRef = null
        if (c.info.tripId == 1 || c.info.tripId == 2) service = orderService
        else service = orderOtherService

        deliver(service.path)(deliveryId =>GetOrderById(c.info.orderId,deliveryId,c.requester,requestId) )

      case c:ResponseFindOrderById =>
        confirmDelivery(c.deliveryId)
        if (c.found) {
         val order   = c.order
          println(c.requester +"  "+c.requestId)
          val info = state.onGoingRequests.get(c.requester,c.requestId).get

          // persistence error
          info.order = c.order
            val totalExpand = state.payments.values.filter(payment=> payment.userId == info.userId)
              .map(payment => payment.price).sum + order.price
            val money = state.moneys.values.filter(money=>money.userId == info.userId).map(money=>money.money).sum
            if (totalExpand <= money) {
              val outsidePaymentInfo = PaymentInfo(info.userId, info.orderId, info.tripId, order.price)

              deliver(paymentService.path)(deliveryId => Pay(outsidePaymentInfo,deliveryId,c.requester,c.requestId) )

            }         else sender() ! Response(1, "Insufficient Money Failure", (totalExpand,money))

        }          else sender() ! Response(1, "Payment Failed, Order Not Exists", null)



      case c: ResponsePayment =>
        confirmDelivery(c.deliverID)
                  if (c.paid) {
                    val info = state.onGoingRequests.get(c.requester, c.requestId).get
                    var service: ActorRef = null
                    if (info.tripId == 1 || info.tripId == 2) service = orderService
                    else service = orderOtherService
                    val orderStatus = 1
                    deliver(service.path)(deliveryId => ModifyOrder(info.orderId, orderStatus, deliveryId, c.requester, c.requestId))
                  } else sender() ! Response(1, "Payment Failed,payment Failure", None)

    case c: ResponseModifyOrder =>
      if (c.modified){
        val requestDetails = state.onGoingRequests.get(c.requester, c.requestId).get
          val payment = Payment2(orderId =requestDetails.orderId,userId = requestDetails.userId, price = requestDetails.price,paymentType = PaymentType().O)
          persist(SavePayment(payment))(updateState)
          deliver(notificationService.path)(deliveryId =>Order_Paid_success(NotifyInfo("", requestDetails.order.id,
            requestDetails.order.contactsName, requestDetails.order.to, requestDetails.order.from,
            requestDetails.order.travelTime, requestDetails.order.travelDate,
            requestDetails.order.seatClass, requestDetails.order.seatNumber, requestDetails.order.price), c.requester,
            deliveryId = deliveryId, requestId = scala.util.Random.nextInt(1000)))
      } else sender() ! Response(1, "Payment Failed, Order Change Failure", None)



      case  c:CreateAccount =>
       if (state.moneys.exists(ac=>ac._2.userId == c.info.userId )) sender() ! Response(1,"Error: acc exists", None)
        else {
         persist(c)(updateState)
         sender() ! Response(0,"Success: acc created", None)
       }
      case  c:AddMoney =>
        state.moneys.get(c.userId,c.deliveryId) match {
          case Some(acc) =>
            sender() ! Response(1,"Error: acc does not exist", None)
          case None =>
            persist(AddMoney(c.deliveryId,c.userId, c.money))(updateState)
            sender() ! Response(0,"Success: Money added", None)

        }
      case  QueryPayment( ) =>
        sender() ! Response(0,"Success",state.payments.values.toList)
      case  QueryAccount( ) =>
       val balances: List[TSCommon.Commons.Balance]=state.moneys.values.map( acc => TSCommon.Commons.Balance(acc.userId,
          acc.money -
          state.payments.values.filter(p => p.userId ==acc.userId).map(acc=>acc.price).sum)).toList
       sender() ! Response(0, "Success", balances)
      case  c:DrawBack =>
        state.moneys.get(c.userId,c.deliveryId) match {
          case Some(_) =>
            println(" =========Inside Service: DrawBackFailure")
            sender() ! ResponseDrawBack(drawnBack = false,c.deliveryId,c.requester,c.requestId,c.requestLabel)

          case None =>
            persist(DrawBack(c.userId, c.money))(updateState)
            println(" =========Inside Service: DrawBackSuccess")
            sender() ! ResponseDrawBack(drawnBack = true,c.deliveryId,c.requester,c.requestId,c.requestLabel)

        }

      case  c:PayDifference2 =>
            val payment = Payment2(orderId =c.info.orderId,userId = c.info.userId, price = c.info.price)
            val totalExpand = state.payments.values.filter(payment=> payment.userId == c.info.userId)
              .map(payment => payment.price).sum + c.info.price
            val money = state.moneys.values.filter(money=>money.userId == c.info.userId).map(money=>money.money).sum

            if (totalExpand > money) {
              val outsidePaymentInfo =  PaymentInfo(c.info.userId,c.info.orderId,c.info.tripId,c.info.price)

              val responseFuture: Future[Any] = paymentService ? Pay(outsidePaymentInfo)
                    val response = Await.result(responseFuture,duration).asInstanceOf[Response]
                  if (response.status == 0){
                    payment.paymentType = PaymentType().E
                    persist(SavePayment(payment))(updateState)
                    sender() ! ResponsePayDifference2(paid=true,c.deliveryId,c.requester,c.requestId)
                  }
                  else sender() ! ResponsePayDifference2(paid=false,c.deliveryId,c.requester,c.requestId)
            }
            else {
              setOrderStatus(c.info.tripId, c.info.orderId)
              payment.paymentType = PaymentType().E
              persist(SavePayment(payment))(updateState)
            }
            sender() !  ResponsePayDifference2(paid=true,c.deliveryId,c.requester,c.requestId)

      case  QueryAddMoney( ) =>
        sender() ! Response(0, "Success", state.moneys.values.toList)
      case c:RequestComplete =>
        confirmDelivery(c.deliveryId)
    }

     def setOrderStatus(tripId: Int, orderId: Int): Boolean = {
       var service: ActorRef = null
      val orderStatus = 1
      //order paid and not collected
      var result = false
       if (tripId == 1 || tripId == 2) service = orderService
       else service = orderOtherService
       val responseFuture: Future[Any] = service ? ModifyOrder(orderId,orderStatus)
       val response = Await.result(responseFuture,duration).asInstanceOf[Response]
       if (response.status == 0) result = true
      result
    }

  }
}