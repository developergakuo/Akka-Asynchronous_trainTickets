import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}


import scala.util.Random

object TSClient {

  class Client(userService: ActorRef,travelPlanService: ActorRef, preserveService: ActorRef, preserveOtherService: ActorRef,
               contactService: ActorRef,stationService: ActorRef, foodMapService: ActorRef, insidePaymentService: ActorRef,
               rebookService: ActorRef,cancelService: ActorRef, orderService: ActorRef, orderOtherService: ActorRef) extends Actor {
    var clientUserName: String = ""
    var accountId: Int = 1
    var contactsId: Int = 1
    var receiver: ActorRef = _
    override def receive: Receive = {
        case  ClientLogin(userName: String, password: String) =>
          receiver = sender()
          println("======== login ")
          clientUserName = userName
          val response: Future[Any] = userService ? FindByUserName2(userName)
          val result = Await.result(response, duration).asInstanceOf[Response]
          if(result.status == 0){
            val user = result.data.asInstanceOf[Account]
            if (user.password.eq(password))  accountId = user.userId
            println("======== accountid: "+ accountId)
          }else{
            sender() ! (1, "Login Error")
          }
          val response2: Future[Any] = contactService ? FindContactsByAccountId(accountId)
          val result2 = Await.result(response2,duration).asInstanceOf[Response]
          if (result2.status == 0){
            val contacts = result2.data.asInstanceOf[Contacts]
            contactsId = contacts.id
            println("======== contactsid: "+ contactsId)
          }
          else{
            sender() ! (1, "Contacts fetch Error")
          }
        case  ClientCancelOrder(order) =>
          receiver = sender()
          if(accountId != -1) {
          //get all orders by account id
          cancelService ! CancelOrder(accountId: Int, order.id)}
        case  ClientRebook(order, trip) =>
          println("======== rebook : "+ accountId)
          receiver = sender()
          if(accountId != -1) {
            rebookService ! Rebook(RebookInfo(accountId,order.id,order.trainNumber,trip.tripId,
              SeatClass().secondClass._1,new Date()))}
        case  ClientPay(order) =>
          receiver = sender()
          if(accountId != -1) {
            insidePaymentService ! Pay(PaymentInfo(accountId, order.id, order.trainNumber, order.price))
          }
        case c:ClientPreserve =>
          println("======== clientBook: " )
          if(accountId != -1){
            receiver = sender()
            //search for the cheapest route
            var travelAdvanceResultUnits: Option[List[TravelAdvanceResultUnit] ] = None
            val trainsFuture: Future[Any] = travelPlanService ? GetCheapest(TripInfo(c.startingStaion,c.endSation,c.travelDate))
            val trainsResponse = Await.result(trainsFuture, duration).asInstanceOf[Response]
            if (trainsResponse.status == 0) travelAdvanceResultUnits = Some(trainsResponse.data.asInstanceOf[List[TravelAdvanceResultUnit]])
            travelAdvanceResultUnits match {
              case Some(units) =>
                println("Units: "+ units)
                if(units.nonEmpty){
                  val unit = units.head
                  //book food
                  val foodStation: String = unit.stopStations(Random.nextInt(unit.stopStations.size))
                  var storeName: String = ""
                  var foodName: String = ""
                  var foodPrice:Double = 0.0
                  // get train id
                  val trainIdFuture: Future[Any] = stationService ? QueryForIdStation(foodStation)
                  val trainIdResponse  = Await.result(trainIdFuture,duration).asInstanceOf[Response]
                  if (trainIdResponse.status == 0) {
                    println("FoodStationIB"+trainIdResponse.data)
                    val stationId = trainIdResponse.data.asInstanceOf[Int]
                    //get food-stores
                    val storesFuture: Future[Any] = foodMapService ? ListFoodStoresByStationId(stationId)
                    val storesResponse = Await.result(storesFuture,duration).asInstanceOf[Response]
                    if (storesResponse.status == 0) {

                      val stores = storesResponse.data.asInstanceOf[List[FoodStore]]
                      if(stores.nonEmpty){
                        val store = stores.head
                        storeName = stores.head.storeName
                        foodName = store.foodList.head.foodName
                        foodPrice = store.foodList.head.price
                        println("Found Foodstore: " + store)
                      }
                      else sender() ! Response(1,"Foodstores Error:",None)
                    }
                  }else sender() ! Response(1,"TrainId Error:",None)
                  //consignee info
                  val handleDate = new Date()
                  val consigneeName = ""
                  val consigneePhone =""
                  val consigneeWeight = 0.0
                  val isWithin = false
                  //preserve
                  val orderInfo = OrderTicketsInfo2(accountId,contactsId,unit.tripId,c.seatType,c.travelDate,unit.fromStationName,
                    unit.toStationName,c.assuranceType,c.foodType,foodStation,storeName,foodName,foodPrice,handleDate,consigneeName,consigneePhone,consigneeWeight,isWithin)
                  if(unit.tripId == 1 || unit.tripId == 2){
                    println("Preserving highSpeedOrder")
                    preserveService ! Preserve(orderInfo)
                  }else{
                    println("Preserving LowSpeedOrder")
                    preserveOtherService ! Preserve(orderInfo)
                  }
                } else sender() ! Response(2,"Error: No routes found",None)
              case None =>
                sender() ! Response(1,"Error: No routes found",None)
            }
          }
        case Response(0, "Success", PreservationSuccess(deliveryId,email)) =>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== PreservationSuccess ")
          receiver ! PreservationSuccess(deliveryId,email)
        case Response(0, "Success", OrderCanceled(deliveryId,email)) =>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== OrderCanceled ")
          receiver ! OrderCanceled(deliveryId,email)
        case Response(0, "Success", OrderCreated(deliveryId,email)) =>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== OrderCreated ")
          receiver ! OrderCreated(deliveryId,email)
        case Response(0, "Success", OrderChanged(deliveryId,email))=>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== OrderChanged ")
          receiver ! OrderChanged(deliveryId, email)
        case Response(0, "Success", OrderPaid(deliveryId,email)) =>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== OrderPaid ")
          receiver ! OrderPaid(deliveryId,email)
        case Response(0, "Success", OrderRebooked(deliveryId,email))=>
          sender() ! ConfirmMailDelivery(deliveryId)
          println("======== OrderRebooked ")
          receiver ! OrderRebooked(deliveryId,email)
        case Response(1, message, _) =>
          println("======== Client General Error: "+ message)
          receiver ! Response(1, message, None)
        case  Response(0, message, _) =>
          println("======== Client General success:  "+ message)
          receiver ! Response(0, message, None)
        case c =>
          println("======== miscellaneous: "+ c)

    }

  }

}
