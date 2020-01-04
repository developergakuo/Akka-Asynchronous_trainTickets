import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}

import InputData.priceConfigs
import TSPriceService.PriceConfigRepository
import akka.persistence._

import scala.util.Random

object TSClient {
  case class UpdateReceiver(actorRef: ActorRef) extends Evt
  case class UpdateUserName(name: String) extends Evt
  case class AccountId(id: Int) extends Evt
  case class ContactsId(id: Int) extends Evt


  case class ClientState ( var clientUserName: String = "", var accountId: Int = 1, var contactsId: Int = 1,var receiver: ActorRef = null)

  class Client(userService: ActorRef, travelPlanService: ActorRef, preserveService: ActorRef, preserveOtherService: ActorRef,
               contactService: ActorRef, stationService: ActorRef, foodMapService: ActorRef, insidePaymentService: ActorRef,
               rebookService: ActorRef, cancelService: ActorRef, orderService: ActorRef, orderOtherService: ActorRef) extends PersistentActor with AtLeastOnceDelivery {
    var state: ClientState = ClientState()


    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "Client-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: ClientState) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case  UpdateReceiver(actorRef) =>
        state.receiver = actorRef
      case  UpdateUserName(name) =>
        state.clientUserName = name
      case  AccountId(id) =>
        state.accountId = id
      case  ContactsId(id) =>
        state.contactsId = id

    }

    override def receiveCommand: Receive = {
      case ClientLogin(userName: String, password: String) =>
        persist(UpdateReceiver(sender()))(updateState)
        println("======== login ")
        persist(UpdateUserName(userName))(updateState)

        val response: Future[Any] = userService ? FindByUserName2(userName)
        val result = Await.result(response, duration).asInstanceOf[Response]
        if (result.status == 0) {
          val user = result.data.asInstanceOf[Account]
          if (user.password.eq(password)) persist(AccountId(user.userId))(updateState)
            
        } else {
          sender() ! (1, "Login Error")
        }
        val response2: Future[Any] = contactService ? FindContactsByAccountId(state.accountId)
        val result2 = Await.result(response2, duration).asInstanceOf[Response]
        if (result2.status == 0) {
          val contacts = result2.data.asInstanceOf[Contacts]
          persist(ContactsId(contacts.id))(updateState)
        }
        else {
          sender() ! (1, "Contacts fetch Error")
        }
      case c:AddMoney=>
        deliver(insidePaymentService.path)(deliveryId => AddMoney(deliveryId,c.userId,c.money))
      case MoneyReceived(deliveryId: Long)=>
        confirmDelivery(deliveryId)

      case ClientCancelOrder(order) =>
        persist(UpdateReceiver(sender()))(updateState)
        if (state.accountId != -1) {
          deliver(cancelService.path)(deliveryId => CancelOrder(state.accountId: Int, order.id, deliveryId = deliveryId, requester = self))
        }

      case ClientRebook(order, trip) =>
        println("======== rebook : " + state.accountId)
        persist(UpdateReceiver(sender()))(updateState)
        if (state.accountId != -1) {
          val info = RebookInfo(state.accountId, order.id, order.trainNumber, trip.tripId,
            SeatClass().secondClass._1, new Date())
          deliver(rebookService.path)(deliveryID => Rebook(info, deliveryID, self, Random.nextInt(1000)))
        }

      case ClientPay(order) =>
        state.receiver = sender()
        if (state.accountId != -1) {
          deliver(insidePaymentService.path)(deliveryID => Pay(PaymentInfo(state.accountId, order.id, order.trainNumber, order.price), deliveryID))

        }
      case c: ClientPreserve =>
        println("======== ClientPreserve: ")
        if (state.accountId != -1) {
          
          persist(UpdateReceiver(sender()))(updateState)
          //search for the cheapest route
          var travelAdvanceResultUnits: Option[List[TravelAdvanceResultUnit]] = None
          val trainsFuture: Future[Any] = travelPlanService ? GetCheapest(TripInfo(c.startingStaion, c.endSation, c.travelDate))
          val trainsResponse = Await.result(trainsFuture, duration).asInstanceOf[Response]
          if (trainsResponse.status == 0) travelAdvanceResultUnits = Some(trainsResponse.data.asInstanceOf[List[TravelAdvanceResultUnit]])
          travelAdvanceResultUnits match {
            case Some(units) =>
              println("Units: " + units)
              if (units.nonEmpty) {
                val unit = units.head
                //book food
                val foodStation: String = unit.stopStations(Random.nextInt(unit.stopStations.size))
                var storeName: String = ""
                var foodName: String = ""
                var foodPrice: Double = 0.0
                // get train id
                val trainIdFuture: Future[Any] = stationService ? QueryForIdStation(0, self, Random.nextInt(1000), foodStation, -1)
                val trainIdResponse = Await.result(trainIdFuture, duration).asInstanceOf[ResponseQueryForIdStation]
                if (trainIdResponse.found) {
                  println("FoodStationIB" + trainIdResponse.stationId)
                  val stationId = trainIdResponse.stationId
                  //get food-stores
                  val storesFuture: Future[Any] = foodMapService ? ListFoodStoresByStationId(stationId)
                  val storesResponse = Await.result(storesFuture, duration).asInstanceOf[Response]
                  if (storesResponse.status == 0) {

                    val stores = storesResponse.data.asInstanceOf[List[FoodStore]]
                    if (stores.nonEmpty) {
                      val store = stores.head
                      storeName = stores.head.storeName
                      foodName = store.foodList.head.foodName
                      foodPrice = store.foodList.head.price
                      println("Found Foodstore: " + store)
                    }
                    else sender() ! Response(1, "Foodstores Error:", None)
                  }
                } else sender() ! Response(1, "TrainId Error:", None)
                //consignee info
                val handleDate = new Date()
                val consigneeName = "Mike"
                val consigneePhone = ""
                val consigneeWeight = 0.0
                val isWithin = false
                //preserve
                val orderInfo = OrderTicketsInfo2(state.accountId,state.contactsId, unit.tripId, c.seatType, c.travelDate, unit.fromStationName,
                  unit.toStationName, c.assuranceType, c.foodType, foodStation, storeName, foodName, foodPrice, handleDate, consigneeName, consigneePhone, consigneeWeight, isWithin)
                if (unit.tripId == 1 || unit.tripId == 2) {
                  println("Preserving highSpeedOrder")
                  deliver(preserveService.path)(deliveryID => Preserve(deliveryID, self,Random.nextInt(1000), orderInfo))

                } else {
                  println("Preserving LowSpeedOrder")
                  deliver(preserveOtherService.path)(deliveryID => Preserve(deliveryID, self,Random.nextInt(1000), orderInfo))
                }
              } else sender() ! Response(2, "Error: No routes found", None)
            case None =>
              sender() ! Response(1, "Error: No routes found :(", None)
          }
        }

      case RebookDelivered(deliveryId) =>
        confirmDelivery(deliveryId)

      case PaymentDelivered(deliveryId) =>
        confirmDelivery(deliveryId)

      case OrderDelivered(deliveryId) =>
        confirmDelivery(deliveryId)

      case CancelOrderDelivered(deliveryId) =>
        confirmDelivery(deliveryId)

      case PreservationDelivered(deliveryId) =>
        confirmDelivery(deliveryId)

      case Response(0, "Success", PreservationSuccess(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== PreservationSuccess ")
        state.receiver ! PreservationSuccess(deliveryId, email)
      case Response(0, "Success", OrderCanceled(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== OrderCanceled ")
        state.receiver ! OrderCanceled(deliveryId, email)
      case Response(0, "Success", OrderCreated(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== OrderCreated ")
        state.receiver ! OrderCreated(deliveryId, email)
      case Response(0, "Success", OrderChanged(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== OrderChanged ")
        state.receiver ! OrderChanged(deliveryId, email)
      case Response(0, "Success", OrderPaid(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== OrderPaid ")
        state.receiver ! OrderPaid(deliveryId, email)
      case Response(0, "Success", OrderRebooked(deliveryId, email)) =>
        sender() ! ConfirmMailDelivery(deliveryId)
        println("======== OrderRebooked ")
        state.receiver ! OrderRebooked(deliveryId, email)
      case Response(1, message, data) =>
        println("======== Client General Error: " + message)
        state.receiver ! Response(1, message, data)
      case Response(0, message, _) =>
        println("======== Client General success:  " + message)
        state.receiver ! Response(0, message, None)
      case c =>
        println("======== miscellaneous: " + c)

    }

  }

}


