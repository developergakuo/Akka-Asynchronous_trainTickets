import TSCommon.Commons._
import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}

import akka.persistence._

import scala.util.Random


object TSRebookService {
  case class RebookState(requests: Map[(ActorRef, Int),RebookRequest])
  case class RebookRequest(rebookInfo: RebookInfo, var order: Option[Order] = None,
                           var tripResponse: Option[TripResponse] = None, var ticketPrice: Double = 0,
                           var tripAllDetail: Option[TripAllDetail] = None, var drawnBack: Boolean =false,
                           var ticket: Option[Ticket] = None, var orderUpdated: Boolean = false)
  case class PayDifferenceState(requests: Map[(ActorRef, Int),PayDifferenceRequest])
  case class PayDifferenceRequest(rebookInfo: RebookInfo, var order: Option[Order] = None,
                                  var tripResponse: Option[TripResponse] = None, var ticketPrice: Double = 0,
                                  var tripAllDetail: Option[TripAllDetail] = None, var drawnBack: Boolean =false,
                                  var ticket: Option[Ticket] = None, var orderUpdated: Boolean = false)
  case class RebookServiceSate(rebooks: RebookState , paydifferences:PayDifferenceState )
  class RebookService (orderService: ActorRef, orderOtherService: ActorRef, travelService: ActorRef,
                       travel2service: ActorRef, stationService: ActorRef, insidePayService: ActorRef, seatService: ActorRef, notificationService: ActorRef)
    extends PersistentActor with AtLeastOnceDelivery {

    var state: RebookServiceSate  =  RebookServiceSate(RebookState(Map()), PayDifferenceState(Map()))

    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "RebookService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: RebookServiceSate) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case e: Rebook =>
        e.requester ! RebookDelivered(e.deliveryId)
        println("rebook event")
        state = RebookServiceSate(RebookState(state.rebooks.requests + ((e.requester, e.requestId) -> RebookRequest(e.info))), state.paydifferences)
        asyncGetOrderByRebookInfo(e.info, "Rebook", e.requester, e.requestId)
      case e: ResponseFindOrderById =>
        confirmDelivery(e.deliveryId)
        if (e.found) {
          if(e.requestLabel.equals("Rebook")){
            val request = state.rebooks.requests.get((e.requester, e.requestId)).get
            request.order = Some(e.order)
            if (e.order.status == OrderStatus().NOTPAID._1) {
              sender() ! Response(1, "You haven't paid the original ticket!", null)
            } else if (e.order.status == OrderStatus().PAID._1) {
              if (!checkTime(e.order.travelDate, e.order.travelTime)) {
                sender() ! (1, "You can only change the ticket before the train start or within 2 hours after the train start.", null)
              } else {
                val gtdi = TripAllDetailInfo(request.rebookInfo.tripId, request.rebookInfo.date, queryForStationName(e.order.from), queryForStationName(e.order.to))
                asyncGetTripAllDetailInformation(gtdi, request.rebookInfo.tripId,e.requester,e.requestId,e.requestLabel)
              }
            }
            else {
              sender() ! (1, "Order status not valid for change.", null)

            }
          }
          else if(e.requestLabel.equals("PayDifference")){
            val request = state.paydifferences.requests.get((e.requester, e.requestId)).get
            val gtdi = TripAllDetailInfo(request.rebookInfo.tripId,request.rebookInfo.date, queryForStationName(e.order.from), queryForStationName(e.order.to))
            // TripAllDetail
            asyncGetTripAllDetailInformation(gtdi, request.rebookInfo.tripId,e.requester,e.requestId, e.requestLabel)
          }

        }
      case e: ResponseGetTripAllDetailInfo =>
        confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get

        if(e.found){
          if(e.label.equals("Rebook")){
            val tripResponse = e.gtdr.tripResponse
            request.tripResponse = Some(tripResponse)
            request.tripAllDetail = Some(e.gtdr)
            if (request.rebookInfo.seatType == SeatClass().firstClass._1) {
              if (tripResponse.confortClass <= 0) {
                sender()! Response  (1, "Seat Not Enough", null)
              }
            } else {
              if (tripResponse.economyClass == SeatClass().secondClass._1) {
                if (tripResponse.economyClass <= 0) {
                  sender()! Response  (1, "Seat Not Enough", null)
                }
              }
            }
            if (request.rebookInfo.seatType == SeatClass().firstClass._1) {
              request.ticketPrice = tripResponse.priceForConfortClass

            } else if (request.rebookInfo.seatType == SeatClass().secondClass._1) {
              request.ticketPrice  = tripResponse.priceForEconomyClass
            }
            val oldPrice: Double = request.order.get.price
            if (oldPrice > request.ticketPrice   ) {
              val difference = oldPrice - request.ticketPrice
              drawBackMoney(request.rebookInfo.loginId, difference, e.requester, e.requestId)

            }
            else if (oldPrice == request.ticketPrice) {

              //do nothing
              updateOrder(request.order.get, request.rebookInfo,request.tripAllDetail.get, request.ticketPrice,e.requester, e.requestId,"Rebook" )
            } else {
              //补差价
              val difference = request.ticketPrice  - oldPrice
              e.requester ! Response (2, "Please pay the different money!", difference)
            }
          }
          else  if(e.label.equals("PayDifference")){
            val request = state.paydifferences.requests.get((e.requester, e.requestId)).get
            var ticketPrice = 0.0
            if (request.rebookInfo.seatType == SeatClass().firstClass._1) {
              ticketPrice = e.gtdr.tripResponse.priceForConfortClass
            } else if (request.rebookInfo.seatType == SeatClass().secondClass._1) {
              ticketPrice = e.gtdr.tripResponse.priceForEconomyClass
            }
            val oldPrice = request.order.get.price
             payDifferentMoney(request.rebookInfo.orderId, request.rebookInfo.tripId,
               request.rebookInfo.loginId, ticketPrice - oldPrice,e.requester,e.requestId)
          }

          }
        else{

        }
      case e: ResponseDrawBack =>
        confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get
        if(e.drawnBack){
          request.drawnBack = true
          updateOrder(request.order.get, request.rebookInfo,request.tripAllDetail.get, request.ticketPrice, e.requester, e.requestId,"Rebook")
        }
        else{

        }
      case e: ResponseDistributeSeat =>
        confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get
        if(e.found){
          request.ticket =Some(e.ticket)
        }
        val order = request.order.get
        val oldTripId = order.trainNumber
        val info = request.rebookInfo
        if ((tripGD(oldTripId) && tripGD(info.tripId)) || (!tripGD(oldTripId) && !tripGD(info.tripId))) {
          println("we are updating your order1: "+ order.status)
          updateOrder(order, info.tripId,e.requester, e.requestId, e.label)
        }
        else {
          deleteOrder(order.id, oldTripId,e.requester,e.requestId,e.label)
        }
      case e:ResponseUpdateOrder =>
        confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get
        if(e.update){
          if(e.requestLabel.equals("Rebook")){
            request.orderUpdated = true
            val order = request.order.get

           deliver(notificationService.path)  (deliveryId => Order_Rebook_success(NotifyInfo(email ="",order.id,order.contactsName,
              order.from,order.to,order.travelTime,order.travelDate,order.seatClass,order.seatNumber,order.price),e.requester,deliveryId = deliveryId, requestId = Random.nextInt(1000)))
          }
          else if(e.requestLabel.equals("PayDifference")){
            e.requester ! Response(0,"Success: Update order", None)

          }
        }
       else e.requester ! Response(1,"Error: Updating order failed", None)
      case e: ResponseDeleteOrder =>
        confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get
        if(e.deleted){
          val order = request.order.get
          createOrder(order, order.trainNumber,e.requester,e.requestId,e.requestLabel)

        }
      case e: ResponseCreateOrder =>
        if(e.requestLabel.equals("Rebook")){

          confirmDelivery(e.deliveryId)
        val request = state.rebooks.requests.get((e.requester, e.requestId)).get
        if(e.created) {
          request.orderUpdated = true
          val order = request.order.get
          deliver(notificationService.path) (deliveryId=>Order_Rebook_success(NotifyInfo(email = "", order.id, order.contactsName, order.from,
            order.to, order.travelTime, order.travelDate, order.seatClass, order.seatNumber, order.price), e.requester,deliveryId = deliveryId, requestId = Random.nextInt(1000)))
        }
          if(e.requestLabel.equals("PayDifference")){
            e.requester ! Response(0,"Success: Update order", None)

          }
        }
        else  e.requester ! Response(1,"Error: Updating order failed", None)

      case e:PayDifference =>
       state = RebookServiceSate(state.rebooks, PayDifferenceState(state.paydifferences.requests + ((e.requester,e.requestId)-> PayDifferenceRequest(rebookInfo = e.info))))
       asyncGetOrderByRebookInfo(e.info, "PayDifference", e.requester, e.requestId)

      case e:ResponsePayDifference2=>
        val request = state.paydifferences.requests.get((e.requester, e.requestId)).get
        updateOrder(request.order.get, request.rebookInfo, request.tripAllDetail.get, request.ticketPrice,e.requester,e.requestId,"PayDifference")

      case e:RequestComplete =>
        confirmDelivery(e.deliveryId)
    }

    override def receiveCommand: Receive = {
      case c: Rebook =>
        println("rebook command")
        persist(c)(updateState)
      case c: ResponseFindOrderById =>
        persist(c)(updateState)
      case c: ResponseGetTripAllDetailInfo =>
        persist(c)(updateState)
      case c: ResponseDrawBack =>
        persist(c)(updateState)
      case c: ResponseDistributeSeat =>
        persist(c)(updateState)
      case c: ResponseUpdateOrder =>
        persist(c)(updateState)
      case c: ResponseDeleteOrder =>
        persist(c)(updateState)
      case c: ResponseCreateOrder =>
        persist(c)(updateState)
      case c: PayDifference =>
        persist(c)(updateState)
      case c:ResponsePayDifference2=>
        persist(c)(updateState)
      case c:RequestComplete =>
        persist(c)(updateState)



    }

    def  updateOrder(order: Order,  info: RebookInfo,  gtdr: TripAllDetail,  ticketPrice: Double, requester: ActorRef, requestId: Int, label: String): Unit = {


      order.trainNumber =info.tripId
      order.boughtDate = new Date()
      order.status = OrderStatus().CHANGE._1
      order.price = ticketPrice//Set ticket price
      order.seatClass = info.seatType
      order.travelDate = info.date
      order.travelTime = gtdr.trip.startingTime
      if (info.seatType == SeatClass().firstClass._1) {//Dispatch the seat
        dispatchSeat(info.date, order.trainNumber, order.from, order.to, SeatClass().firstClass._1,requester, requestId,label)
      } else {
          dispatchSeat(info.date, order.trainNumber, order.from, order.to, SeatClass().secondClass._1,requester, requestId,label)
      }
    }

    def  dispatchSeat( date : Date,  tripId: Int,  startStationId: Int,  endStataionId: Int,  seatType: Int, requester: ActorRef, requestId: Int, label: String): Unit = {
      val seatRequest =  Seat(date,tripId,startStationId,endStataionId,seatType)
        deliver(seatService.path) (deliveryId => DistributeSeat(deliveryId,requester, requestId,seatRequest,label))
    }

    private def tripGD(tripId: Int) = if (tripId == 1 || tripId == 2) true
    else false

    def  checkTime( travelDate: Date,  travelTime: Date): Boolean = {
       var result = true
      val calDateA = Calendar.getInstance()
      val today = new Date()
      calDateA.setTime(today)
      val calDateB = Calendar.getInstance()
      calDateB.setTime(travelDate)
      val calDateC = Calendar.getInstance()
      calDateC.setTime(travelTime)
      if (calDateA.get(Calendar.YEAR) > calDateB.get(Calendar.YEAR)) {
        result = false
      } else if (calDateA.get(Calendar.YEAR) == calDateB.get(Calendar.YEAR)) {
        if (calDateA.get(Calendar.MONTH) > calDateB.get(Calendar.MONTH)) {
          result = false
        } else if (calDateA.get(Calendar.MONTH) == calDateB.get(Calendar.MONTH)) {
          if (calDateA.get(Calendar.DAY_OF_MONTH) > calDateB.get(Calendar.DAY_OF_MONTH)) {
            result = false
          } else if (calDateA.get(Calendar.DAY_OF_MONTH) == calDateB.get(Calendar.DAY_OF_MONTH)) {
            if (calDateA.get(Calendar.HOUR_OF_DAY) > calDateC.get(Calendar.HOUR_OF_DAY) + 2) {
              result = false
            } else if (calDateA.get(Calendar.HOUR_OF_DAY) == calDateC.get(Calendar.HOUR_OF_DAY) + 2) {
              if (calDateA.get(Calendar.MINUTE) > calDateC.get(Calendar.MINUTE)) {
                result = false
              }
            }
          }
        }
      }
      result
    }


    def  asyncGetTripAllDetailInformation(gtdi: TripAllDetailInfo, tripId: Int, requester: ActorRef, requestId: Int, requestLabel:String):Unit = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = travelService
      else service = travel2service
      deliver(service.path)(deliveryId=> GetTripAllDetailInfo(deliveryId,requester,requestId,gtdi,label =requestLabel,sender = Some(self)))
    }

    def  createOrder( order: Order,  tripId: Int, requester: ActorRef, requestId: Int, label: String): Unit = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      deliver(service.path)(deliveryId=>  CreateOrder(deliveryId,requester,requestId,order,requestLabel = label))
    }

    def  updateOrder( order: Order,  tripId: Int,requester: ActorRef, requestId: Int,label: String):Unit =  {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
     deliver(service.path)(deliveryId => UpdateOrder(order,deliveryId,requester,requestId, label))

    }

    def  deleteOrder( orderId: Int,  tripId: Int,requester: ActorRef, requestId: Int, label: String): Unit = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      deliver(service.path)(deliveryId =>  DeleteOrder(orderId,deliveryId,requester, requestId, requestLabel = label))
    }

    def  asyncGetOrderByRebookInfo(info: RebookInfo, requestLabel: String, requester: ActorRef, requestId: Int): Unit = {
      println("======== getOrderByID: ")
      var service: ActorRef = null
      if (info.oldTripId == 1 || info.oldTripId == 2){
        println("======== getOrderByID service: " )
      service = orderService
    }
      else {
        println("======== getOrderByID service: Other " )
        service = orderOtherService
      }

      deliver(service.path)(deliveryId =>GetOrderById(info.orderId,deliveryId,requester,requestId,requestLabel))
    }

    def  queryForStationName( stationId: Int):String = {
      var stationName: Option[String]= None
      val responseFuture: Future[Any] = stationService ? QueryByIdStation(stationId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) stationName =Some(response.data.asInstanceOf[String])
      stationName.get
    }

    def  payDifferentMoney(orderId: Int,  tripId: Int,  userId: Int, money: Double, requester: ActorRef, requestId: Int): Unit = {
      val info: PaymentInfo = PaymentInfo(orderId, tripId, userId, money)

      deliver(insidePayService.path)(deliveryId => PayDifference2(info,deliveryId,requester,requestId))

    }

    def  drawBackMoney( userId: Int,  money: Double,requester: ActorRef, requestId: Int ): Unit={
    deliver(insidePayService.path) (deliveryId => DrawBack(userId,money,deliveryId,requester,requestId) )

    }

    }

  }

