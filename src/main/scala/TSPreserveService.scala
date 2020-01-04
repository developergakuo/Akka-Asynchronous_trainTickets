import TSCommon.Commons.{Response, _}
import akka.actor. ActorRef
import akka.persistence._
import java.util. Date

object TSPreserveService {
  case class Service(var oti: Option[OrderTicketsInfo2] = None, var getContactsById: Option[Contacts] = None,var tripAllDetail: Option[TripAllDetail] = None,
                     var fromStationId: Int = -1, var toStationId: Int = -1, var resultForTravel: Option[TravelResult] = None, var ticket: Option[Ticket] = None,
                     var orderCreated: Option[Order] = None, var checkSecurity:  Boolean = false,var  createFoodOrder: Boolean = false,
                     var consignRequest: Boolean = false, var addAssuranceForOrder: Boolean =false, var account: Option[Account] = None,
                     var consigned: Boolean = false)

  case class PreserveOtherServiceState(requests: Map[(ActorRef,Int),Service])

  class PreserveService (ticketInfoService: ActorRef,
                         securityService: ActorRef, contactService:  ActorRef, travelService: ActorRef,
                         stationService: ActorRef, seatService: ActorRef, orderOtherService: ActorRef,
                         assuranceService: ActorRef, foodService: ActorRef, consignService: ActorRef,
                         userService: ActorRef, notifyService: ActorRef) extends PersistentActor with AtLeastOnceDelivery {

    var state: PreserveOtherServiceState = PreserveOtherServiceState(Map())

    override def preStart(): Unit = {
      println("UserService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("UserService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "PreserveService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: PreserveOtherServiceState) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("UserService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case e: Preserve ⇒
        e.actorRef ! PreservationDelivered(e.deliveryId)
        val service = state.requests.getOrElse((e.actorRef, e.orderNumber), Service())
        service.oti = Some(e.oti)
        state =PreserveOtherServiceState (state.requests + ((e.actorRef, e.orderNumber) -> service))
        deliver(securityService.path)(deliveryId => Check(e.actorRef, e.orderNumber, deliveryId, e.oti.accountId))
      case e: SecurityCheckResponse =>
        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        println("service:"+ service)
        service.checkSecurity = e.isSecure

        if (e.isSecure) deliver(contactService.path)(deliveryId => FindContactsById(deliveryId, e.requester, e.requestId, service.oti.get.contactsId))
        else e.requester ! Response(1, "Order not Secured", None)

      case e: ContactResponse =>
        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        service.getContactsById = e.contacts
        e.contacts match {
          case Some(contacts) =>
            service.getContactsById = Some(contacts)
            val gtdi: TripAllDetailInfo =
              TripAllDetailInfo(service.oti.get.tripId, service.oti.get.date, service.oti.get.from, service.oti.get.to)
            deliver(travelService.path)(deliveryId => GetTripAllDetailInfo(deliveryId, e.requester, e.requestId, gtdi, sender = Some(self) ))
          case None =>
            e.requester ! Response(1, "User contacts not found", None)

        }
      case e: ResponseGetTripAllDetailInfo =>
        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        service.tripAllDetail = Some(e.gtdr)
        if (e.found) {
          if ((service.oti.get.seatType == SeatClass().firstClass._1) && (e.gtdr.tripResponse.confortClass == 0))
            sender() ! Response(1, "First class seats not enough", None)
          else if ((e.gtdr.tripResponse.economyClass == SeatClass().secondClass._1) && (e.gtdr.tripResponse.confortClass == 0))
            sender() ! Response(1, "Economy class seats not enough", None)
          else {
            deliver(stationService.path)(deliveryId => QueryForIdStation(deliveryId, e.requester, e.requestId, service.oti.get.from, 1))
            deliver(stationService.path)(deliveryId => QueryForIdStation(deliveryId, e.requester, e.requestId, service.oti.get.to, 2))
          }
        }
        else sender() ! Response(1, "Trip all detail info Error", None)

      case e: ResponseQueryForIdStation =>
        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if (e.toOrFRom == 1) service.toStationId = e.stationId
        else if (e.toOrFRom == 2) service.fromStationId = e.stationId
        //Are to and from stations set?
        if (service.toStationId != -1 && service.fromStationId != -1) {
          val contacts = service.getContactsById.get
          val trip: Trip = service.tripAllDetail.get.trip
          val oti = service.oti.get
          val order: Order = Order(id = scala.util.Random.nextInt(100000), boughtDate = new Date(),
            status = OrderStatus().NOTPAID._1, contactsDocumentNumber = contacts.documentNumber,
            documentType = contacts.documentType, contactsName = contacts.name, from = service.fromStationId,
            to = service.toStationId, trainNumber = oti.tripId, accountId = oti.accountId, seatClass = oti.seatType,
            travelDate = service.oti.get.date, travelTime = service.tripAllDetail.get.tripResponse.startingTime)
          service.orderCreated = Some(order)
          val query: Travel = Travel(trip, oti.from, oti.to, new Date())
          deliver(ticketInfoService.path)(deliveryID => QueryForTravel(deliveryID, e.requester, e.requestId, query))
        }
      case e: ResponseQueryForTravel =>
        confirmDelivery(e.deliveryID)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if (e.found)  {
          service.resultForTravel = Some(e.travel)
          val oti = service.oti.get
          val order = service.orderCreated.get
          val fromStationId = service.fromStationId
          val toStationId = service.toStationId
          if (oti.seatType == SeatClass().firstClass._1) {
            //Dispatch the seat 1st class
            val seat = Seat( oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().firstClass._1)
            deliver(seatService.path)(deliveryId => DistributeSeat(deliveryId, e.requester, e.requestId, seat))
          }
          else {
            //Dispatch the seat 2nd class
            val seat = Seat( oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().secondClass._1)
            deliver(seatService.path)(deliveryId => DistributeSeat(deliveryId, e.requester, e.requestId, seat))

          }

        }
        else sender() ! Response(1, "Trip is not feasible:", None)
      case e: ResponseDistributeSeat =>
        println("distribute seat success resp")
        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        val order = service.orderCreated.get
        if (e.found)  {
          if (e.seatClass == 1){
            order.seatNumber = e.ticket.seatNo
            order.seatClass = SeatClass().firstClass._1
            order.price = service.resultForTravel.get.prices.get("comfortClass").get
          }
          else if (e.seatClass == 2){
            order.seatNumber = e.ticket.seatNo
            order.seatClass = SeatClass().secondClass._1
            order.price = service.resultForTravel.get.prices.get("comfortClass").get
          }
        }

        deliver(orderOtherService.path)(deliveryID => Create(deliveryID, e.requester, e.requestId, order))
      case e: ResponseCreate =>
        println("order create resp")

        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if (e.created) {
          val oti = service.oti.get
          if (!(oti.assurance == 0)) { //insure
            deliver(assuranceService.path)(deliveryId => CreateAssurance(deliveryId, e.requester, e.requestId, oti.assurance, e.newOrder.id)
            )
          }

          else sender() ! Response(1, "order creation error:", None)
        }

      case e: ResponseCreateAssurance =>
        println("assurance create resp")

        confirmDelivery(e.deliveryId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if (e.created) {
          val oti = service.oti.get
          val ord = service.orderCreated.get
          // create food order
          if (oti.foodType != 0) {
            println("Preserving food ")
            val foodOrder: FoodOrder = FoodOrder(orderId = ord.id, foodType = oti.foodType, price = oti.foodPrice, foodName = oti.foodName)
            if (oti.foodType == 2) {
              foodOrder.stationName = oti.stationName
              foodOrder.storeName = oti.storeName
            }
            deliver(foodService.path)(deliverId => CreateFoodOrder(deliverId, e.requester,e.requestId,foodOrder))
          }

        }
        else sender() ! Response(1, "Assurance creation error:", None)

      case e: ResponseCreateFoodOrder =>

        println("ResponseCreateFoodOrder create resp")
        confirmDelivery(e.deliverId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if(e.created){
          val oti = service.oti.get
          val ord = service.orderCreated.get
          // consignee business
          if (null != oti.consigneeName && !("" == oti.consigneeName)) {
            println("Also needs consignment")
            val consignRequest: Consign = Consign(orderId = ord.id, accountId = ord.accountId,
              handleDate = oti.handleDate, targetDate = ord.travelDate, from = ord.from, to = ord.to,
              consignee = oti.consigneeName, phone = oti.consigneePhone, weight = oti.consigneeWeight,
              isWithin = oti.isWithin)
            deliver(consignService.path)(deliverId => InsertConsignRecord(deliverId, e.requester,e.requestId,consignRequest))
          }

        }
        else sender() ! Response(1, "FoodOrder creation error:", None)
      case e: ResponseInsertConsignRecord =>
        println("ResponseInsertConsignRecord create resp")

        confirmDelivery(e.deliverId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        if(e.created){
          service.consigned = true
        }
        else sender() ! Response(1, "Consign creation error:", None)
        deliver(userService.path)(deliverId => FindByUserId2(deliverId, e.requester,e.requestId,service.orderCreated.get.accountId))

      case e:ResponseFindByUserId2 =>
        confirmDelivery(e.deliverId)
        val service = state.requests.getOrElse((e.requester, e.requestId), Service())
        e.account match {
          case Some(account) =>
            val order = service.orderCreated.get
            service.account =  e.account
            val notifyInfo: NotifyInfo = NotifyInfo(account.email, order.id, account.userName, order.from, order.to,
              order.travelTime, new Date, order.seatClass, order.seatNumber, order.price)
            deliver(notifyService.path)(deliverId => Preserve_success(deliverId, e.requester,e.requestId,notifyInfo,sender()))
          case None =>
            sender() !   Response(1, "User Account not Found:", None)
        }

      case e: RequestComplete =>
        confirmDelivery(e.deliveryId)
        state = PreserveOtherServiceState(state.requests - ((e.Requester,e.requestId)))
    }

    override def receiveCommand: Receive = {
      case c:Preserve=>
        persistAsync(c)(updateState)
      //check order securitys
      case c: SecurityCheckResponse =>
        persistAsync(c)(updateState)

      case c: ContactResponse =>
        persistAsync(c)(updateState)

      case c: ResponseGetTripAllDetailInfo =>
        persistAsync(c)(updateState)

      case c: ResponseQueryForIdStation =>
        persistAsync(c)(updateState)

      case c: ResponseQueryForTravel =>
        persistAsync(c)(updateState)

      case c:ResponseDistributeSeat =>
        persistAsync(c)(updateState)

      case c: ResponseCreate =>
        persistAsync(c)(updateState)

      case c: ResponseCreateAssurance =>
        persistAsync(c)(updateState)

      case c: ResponseCreateFoodOrder =>
        persistAsync(c)(updateState)

      case c: ResponseInsertConsignRecord =>
        persistAsync(c)(updateState)
      case c:ResponseFindByUserId2 =>
        persistAsync(c)(updateState)
      case c: RequestComplete =>
        persistAsync(c)(updateState)

    }

  }
}