
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

object TSRebookService {
  class TSRebookService extends Actor {
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null
    var travelService: ActorRef = null
    var travel2service: ActorRef = null
    var stationService: ActorRef = null
    var insidePayService: ActorRef = null
    var seatservice: ActorRef = null

    override def receive: Receive = {
      case c:Rebook =>
         getOrderByRebookInfo(c.info) match {
          case Some(order) =>
            val status = order.status
            if (status == OrderStatus().NOTPAID._1) {
              sender() ! Response  (1, "You haven't paid the original ticket!", null)
            } else if (status == OrderStatus().PAID._1) {
              // do nothing
            } else if (status == OrderStatus().CHANGE._1) {
              sender() ! Response  (1, "You have already changed your ticket and you can only change one time.", null)
            } else if (status == OrderStatus().COLLECTED._1) {
              sender() ! Response(1, "You have already collected your ticket and you can change it now.", null)
            } else {
               sender() ! Response (1, "You can't change your ticket.", null)
            }
            if (!checkTime(order.travelDate, order.travelTime)) {
              sender() ! (1, "You can only change the ticket before the train start or within 2 hours after the train start.", null)
            }
            val gtdi =  TripAllDetailInfo(c.info.tripId,c.info.date,queryForStationName(order.from),queryForStationName(order.to))
              getTripAllDetailInformation(gtdi, c.info.tripId) match {
                case None => sender() ! Response  (1, "Error", null)
                case Some(tripAllDetail)=>
                  val tripResponse = tripAllDetail.tripResponse
                  if (c.info.seatType == SeatClass().firstClass._1) {
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
                  var ticketPrice = 0.0
                  if (c.info.seatType == SeatClass().firstClass._1) {
                    ticketPrice = tripResponse.priceForConfortClass
                  } else if (c.info.seatType == SeatClass().secondClass._1) {
                    ticketPrice = tripResponse.priceForEconomyClass
                  }
                  val oldPrice: Double = order.price()
                  if (oldPrice > ticketPrice  ) {
                    val difference = oldPrice - ticketPrice
                    if (!drawBackMoney(c.info.loginId, difference)) {
                      sender() ! Response (1, "Can't draw back the difference money, please try again!", null)
                    }
                     if(updateOrder(order, c.info, tripAllDetail,ticketPrice)) sender() ! (0,"Success", None)
                     else  sender() ! (1,"error in order update", None)
                  } else if (oldPrice == ticketPrice) {
                    //do nothing
                    if( updateOrder(order, c.info, tripAllDetail, ticketPrice)) sender() ! (0,"Success", None)
                    else sender() ! (1,"error in order update", None)
                  } else {
                    //补差价
                    val difference = ticketPrice - oldPrice
                    sender() ! Response (2, "Please pay the different money!", difference)
                  }
              }
          case None =>
            Response(1, "order not found", null);

         }
      case c:PayDifference =>
        getOrderByRebookInfo(c.info) match {
          case None => sender() ! Response(1, "Order could not be found", null)
          case Some(order) =>
            val gtdi = TripAllDetailInfo(c.info.tripId,c.info.date,queryForStationName(order.from),queryForStationName(order.to))
            // TripAllDetail
            val gtdr = getTripAllDetailInformation(gtdi, c.info.tripId).get
            var ticketPrice = 0.0
            if (c.info.seatType == SeatClass().firstClass._1) {
            ticketPrice = gtdr.tripResponse.priceForConfortClass
            } else if (c.info.seatType == SeatClass().secondClass._1) {
            ticketPrice = gtdr.tripResponse.priceForEconomyClass
            }
            val oldPrice = order.price


            if (payDifferentMoney(c.info.orderId, c.info.tripId, c.info.loginId, ticketPrice - oldPrice)) {
            if (updateOrder(order, c.info, gtdr, ticketPrice)) sender() ! Response(0, "Success",None)
            else sender() ! Response(1, "Update Failure",None)
            } else {
             sender() ! Response(1, "Can't pay the difference,please try again", null)
            }}
    }

    def  updateOrder(order: Order,  info: RebookInfo,  gtdr: TripAllDetail,  ticketPrice: Double): Boolean = {
      val oldTripId = order.trainNumber
      var seatClass = -1
      var seatNo = -1
      if (info.seatType == SeatClass().firstClass._1) {//Dispatch the seat
        val ticket =
          dispatchSeat(info.date, order.trainNumber, order.from, order.to, SeatClass().firstClass._1)
        seatClass = SeatClass().firstClass._1
        seatNo =ticket.seatNo
      } else {
         val ticket: Ticket =
          dispatchSeat(info.date,
            order.trainNumber, order.from, order.to,
            SeatClass().secondClass._1)
        seatClass = SeatClass().secondClass._1
        seatNo = ticket.seatNo
      }
      if ((tripGD(oldTripId) && tripGD(info.tripId())) || (!tripGD(oldTripId) && !tripGD(info.tripId()))) {

        val changeOrderResult = updateOrder(order, info.tripId)
        if (changeOrderResult == 0) true         else false
      }
      else {
        deleteOrder(order.id(), oldTripId)
        createOrder(order, order.trainNumber)
        true
      }
    }

    def  dispatchSeat( date : Date,  tripId: Int,  startStationId: Int,  endStataionId: Int,  seatType: Int): Ticket = {
      val seatRequest =  Seat(date,tripId,startStationId,endStataionId,seatType)
      var ticket: Option[Ticket]= None
      val response: Future[Any] = seatservice ? DistributeSeat(seatRequest)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) ticket = Some(res.asInstanceOf[Response].data.asInstanceOf[Ticket])
        case Failure(_) =>
          ticket = None
      }
      ticket.get
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


    def  getTripAllDetailInformation( gtdi: TripAllDetailInfo,  tripId: Int):Option[TripAllDetail] = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = travelService
      else service = travel2service

      var tripAllDetail: Option[TripAllDetail] = None
      val response: Future[Any] = service ? GetTripAllDetailInfo(gtdi)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) tripAllDetail = Some(res.asInstanceOf[Response].data.asInstanceOf[TripAllDetail])
          else tripAllDetail = None
        case Failure(_) =>
          tripAllDetail = None
      }
      tripAllDetail
    }

    def  createOrder( order: Order,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService

      var createOrderResponse: Int = -1
      val response: Future[Any] = service ? Create(order)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) createOrderResponse = 0
        case Failure(_) =>
          createOrderResponse = -1
      }
      createOrderResponse
    }

    def  updateOrder( order: Order,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService

      var updateOrderResponse: Int = -1
      val response: Future[Any] = service ? UpdateOrder(order)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) updateOrderResponse = 0
        case Failure(_) =>
          updateOrderResponse = -1
      }
      updateOrderResponse
    }

    def  deleteOrder( orderId: Int,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      var deleteOrderResponse: Int = -1
      val response: Future[Any] = service ? DeleteOrder(orderId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) deleteOrderResponse = 0
        case Failure(_) =>
          deleteOrderResponse = -1
      }
      deleteOrderResponse
    }

    def  getOrderByRebookInfo(info: RebookInfo): Option[Order]= {
      var service: ActorRef = null
      if (info.oldTripId == 1 || info.oldTripId == 2) service = orderService
      else service = orderOtherService
      var order: Option[Order] = None
      val response: Future[Any] = service ? GetOrderById(info.orderId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) order = Some(res.asInstanceOf[Response].data.asInstanceOf[Order])
        case Failure(_) =>
          order = None
      }
      order
    }

    def  queryForStationName( stationId: Int):String = {
      var stationName: Option[String]= None
      val response: Future[Any] = stationService ? QueryByIdStation(stationId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) stationName =Some(res.asInstanceOf[Response].data.asInstanceOf[String])
        case Failure(_) =>
          stationName = None
    }
      stationName.get
    }

    def  payDifferentMoney(orderId: Int,  tripId: Int,  userId: Int, money: Double): Boolean = {
      val info: PaymentInfo = PaymentInfo(orderId, tripId, userId, money)
      var paydifferenceResponse: Int = 1
      val response: Future[Any] = insidePayService ? PayDifference2(info)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) paydifferenceResponse = 0
        case Failure(_) =>
          paydifferenceResponse = -1

      }

      if (paydifferenceResponse == 0) true
      else false
    }

    def   drawBackMoney( userId: Int,  money: Double): Boolean={
      var drawbackResponse: Int = 1
      val response: Future[Any] = insidePayService ? DrawBack(userId,money)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) drawbackResponse = 0
        case Failure(_) =>
          drawbackResponse = -1
      }
      if (drawbackResponse == 0) true
          else false

    }

    }

  }

