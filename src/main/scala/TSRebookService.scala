import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}


object TSRebookService {
  class RebookService (orderService: ActorRef, orderOtherService: ActorRef, travelService: ActorRef,
                       travel2service: ActorRef, stationService: ActorRef, insidePayService: ActorRef, seatService: ActorRef, notificationService: ActorRef) extends Actor {
    override def receive: Receive = {
      case c:Rebook =>
        println("======== rebookService: Rebook ")
         getOrderByRebookInfo(c.info) match {
          case Some(order) =>
            val status = order.status
            if (status == OrderStatus().NOTPAID._1) {
              sender() ! Response  (1, "You haven't paid the original ticket!", null)
            } else if (status == OrderStatus().PAID._1) {
              if (!checkTime(order.travelDate, order.travelTime)) {
                sender() ! (1, "You can only change the ticket before the train start or within 2 hours after the train start.", null)
              } else{
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
                    val oldPrice: Double = order.price
                    if (oldPrice > ticketPrice  ) {
                      val difference = oldPrice - ticketPrice
                      if (!drawBackMoney(c.info.loginId, difference)) {
                        sender() ! Response (1, "Can't draw back the difference money, please try again!", null)
                      }
                      if(updateOrder(order, c.info, tripAllDetail,ticketPrice)){
                        println("We are here")
                        notificationService ! Order_Rebook_success(NotifyInfo(email ="",order.id,order.contactsName,
                          order.from,order.to,order.travelTime,order.travelDate,order.seatClass,order.seatNumber,order.price),sender())
                      }
                      else  sender() ! (1,"error in order update", None)
                    } else if (oldPrice == ticketPrice) {
                      //do nothing
                      if( updateOrder(order, c.info, tripAllDetail, ticketPrice))
                        notificationService ! Order_Rebook_success(NotifyInfo(email ="",order.id,order.contactsName,
                          order.from,order.to,order.travelTime,order.travelDate,order.seatClass,order.seatNumber,order.price),sender())
                      else sender() ! (1,"error in order update", None)
                    } else {
                      //补差价
                      val difference = ticketPrice - oldPrice
                      sender() ! Response (2, "Please pay the different money!", difference)
                    }
                }
                // do nothing
              }
            }
            else if (status == OrderStatus().CHANGE._1) {
              sender() ! Response  (1, "You have already changed your ticket and you can only change one time.", null)
            }
            else if (status == OrderStatus().COLLECTED._1) {
              sender() ! Response(1, "You have already collected your ticket and you can change it now.", null)
            }
            else {
               sender() ! Response (1, "You can't change your ticket.", null)
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
      if ((tripGD(oldTripId) && tripGD(info.tripId)) || (!tripGD(oldTripId) && !tripGD(info.tripId))) {
        val changeOrderResult = updateOrder(order, info.tripId)
        if (changeOrderResult == 0) {
          println("But we are here")
          true
        }
        else {
          false
        }
      }
      else {
        deleteOrder(order.id, oldTripId)
        createOrder(order, order.trainNumber)
        true
      }
    }

    def  dispatchSeat( date : Date,  tripId: Int,  startStationId: Int,  endStataionId: Int,  seatType: Int): Ticket = {
      val seatRequest =  Seat(date,tripId,startStationId,endStataionId,seatType)
      var ticket: Option[Ticket]= None
      val responseFuture: Future[Any] = seatService ? DistributeSeat(seatRequest)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) ticket = Some(response.data.asInstanceOf[Ticket])
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
      val responseFuture: Future[Any] = service ? GetTripAllDetailInfo(gtdi)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) tripAllDetail = Some(response.data.asInstanceOf[TripAllDetail])
      tripAllDetail
    }

    def  createOrder( order: Order,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      var createOrderResponse: Int = -1
      val responseFuture: Future[Any] = service ? Create(order)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) createOrderResponse = 0
      createOrderResponse
    }

    def  updateOrder( order: Order,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      var updateOrderResponse: Int = -1
      val responseFuture: Future[Any] = service ? UpdateOrder(order)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) updateOrderResponse = 0
      updateOrderResponse
    }

    def  deleteOrder( orderId: Int,  tripId: Int): Int = {
      var service: ActorRef = null
      if (tripId ==1  || tripId == 2 ) service = orderService
      else service = orderOtherService
      var deleteOrderResponse: Int = -1
      val responseFuture: Future[Any] = service ? DeleteOrder(orderId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) deleteOrderResponse = 0
      deleteOrderResponse
    }

    def  getOrderByRebookInfo(info: RebookInfo): Option[Order]= {
      println("======== getOrderByID: ")
      var service: ActorRef = null
      if (info.oldTripId == 1 || info.oldTripId == 2){
        println("======== getOrderByID serice: " )
      service = orderService
    }
      else {
        println("======== getOrderByID serice: Other " )
        service = orderOtherService
      }

      var order: Option[Order] = None
      val responseFuture: Future[Any] = service ? GetOrderById(info.orderId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) {
        println("======== getOrderByID: success")
        order = Some(response.data.asInstanceOf[Order])
      }


      order
    }

    def  queryForStationName( stationId: Int):String = {
      var stationName: Option[String]= None
      val responseFuture: Future[Any] = stationService ? QueryByIdStation(stationId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) stationName =Some(response.data.asInstanceOf[String])
      stationName.get
    }

    def  payDifferentMoney(orderId: Int,  tripId: Int,  userId: Int, money: Double): Boolean = {
      val info: PaymentInfo = PaymentInfo(orderId, tripId, userId, money)
      var paydifferenceResponse: Int = 1
      val responseFuture: Future[Any] = insidePayService ? PayDifference2(info)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) paydifferenceResponse = 0
      if (paydifferenceResponse == 0) true
      else false
    }

    def  drawBackMoney( userId: Int,  money: Double): Boolean={
      val responseFuture: Future[Any] = insidePayService ? DrawBack(userId,money)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) {
        println("DrawBackSuccess:")
        true
      }
      else false
    }

    }

  }

