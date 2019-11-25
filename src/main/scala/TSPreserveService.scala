import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import java.util. Date


object TSPreserveService {
  class PreserveService (ticketInfoService: ActorRef ,
                              securityService: ActorRef , contactService:  ActorRef , travelService: ActorRef , stationService: ActorRef , seatService: ActorRef ,
                              orderService: ActorRef ,assuranceService: ActorRef ,
                              foodService: ActorRef , consignService: ActorRef , userService: ActorRef , notifyService: ActorRef) extends Actor {


    override def receive: Receive = {
      case Preserve(oti: OrderTicketsInfo2) =>
        //checkOrder security
        if (!checkSecurity(oti.accountId)) {
          sender() ! Response(1, "Security Error", None)
        }
        else {
          //get contacts
          getContactsById(oti.contactsId) match {
            case Some(contacts) =>
              val gtdi: TripAllDetailInfo = TripAllDetailInfo(oti.tripId, oti.date, oti.from, oti.to)
              getTripAllDetailInformation(gtdi) match {
                case Some(gtdr) =>
                  val tripResponse: TripResponse = gtdr.tripResponse
                  //enough Seats?
                  if ((oti.seatType == SeatClass().firstClass._1) && (tripResponse.confortClass == 0))
                    sender() ! Response(1, "First class seats not enough", None)
                  else if ((tripResponse.economyClass == SeatClass().secondClass._1) && (tripResponse.confortClass == 0))
                    sender() ! Response(1, "Economy class seats not enough", None)
                  else {
                    val trip: Trip = gtdr.trip
                    val fromStationId: Int = queryForStationId(oti.from).get
                    val toStationId: Int = queryForStationId(oti.to).get
                    //make order
                    val order: Order = Order(id = scala.util.Random.nextInt(100000), boughtDate = new Date(),
                      status = OrderStatus().NOTPAID._1, contactsDocumentNumber = contacts.documentNumber,
                      documentType = contacts.documentType, contactsName = contacts.name, from = fromStationId,
                      to = toStationId, trainNumber = oti.tripId, accountId = oti.accountId, seatClass = oti.seatType,
                      travelDate = oti.date, travelTime = gtdr.tripResponse.startingTime)
                    val query: Travel = Travel(trip, oti.from, oti.to, new Date())
                    // Check if the route exists etc
                    val responseFuture: Future[Any] = ticketInfoService ? QueryForTravel(query)
                    val response = Await.result(responseFuture,duration).asInstanceOf[Response]
                    if (response.status == 0) {
                          val resultForTravel: TravelResult = response.data.asInstanceOf[TravelResult]
                          if (oti.seatType == SeatClass().firstClass._1) {
                            //Dispatch the seat 1st class
                            val ticket: Ticket = dispatchSeat(oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().firstClass._1).get
                            order.seatNumber = ticket.seatNo
                            order.seatClass = SeatClass().firstClass._1
                            order.price = resultForTravel.prices.get("comfortClass").get
                          }
                          else {
                            //Dispatch the seat 2nd class
                            val ticket: Ticket = dispatchSeat(oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().secondClass._1).get
                            order.seatNumber = ticket.seatNo
                            order.seatClass = SeatClass().secondClass._1
                            order.price = resultForTravel.prices.get("economyClass").get
                          }
                          createOrder(order) match {
                            case Some(ord) =>
                              //Assure the order
                              if (!(oti.assurance == 0)) { //insure
                                addAssuranceForOrder(oti.assurance, ord.id) match {
                                  case Some(int) =>
                                    if (int != 0) {
                                      sender() ! Response(1, "Insurance creation error:", None)
                                    }
                                  case None =>
                                    sender() ! Response(1, "Insurance creation error:", None)
                                }
                                // create food order
                                if (oti.foodType != 0) {
                                  val foodOrder: FoodOrder = FoodOrder(orderId = ord.id, foodType = oti.foodType, price = oti.foodPrice, foodName = oti.foodName)
                                  if (oti.foodType == 2) {
                                    foodOrder.stationName = oti.stationName
                                    foodOrder.storeName = oti.storeName
                                  }
                                  createFoodOrder(foodOrder) match {
                                    case Some(code) =>
                                      if (code != 0) {
                                        // buy food fail
                                        sender() ! Response(1, "FoodOrder creation error:", None)
                                      }
                                    case None =>
                                    // buy food fail
                                      sender() ! Response(1, "FoodOrder creation error:", None)
                                  }
                                }
                                // consignee business
                                if (null != oti.consigneeName && !("" == oti.consigneeName)) {
                                  val consignRequest: Consign = Consign(orderId = ord.id, accountId = ord.accountId,
                                    handleDate = oti.handleDate, targetDate = ord.travelDate, from = ord.from, to = ord.to,
                                    consignee = oti.consigneeName, phone = oti.consigneePhone, weight = oti.consigneeWeight,
                                    isWithin = oti.isWithin)
                                  createConsign(consignRequest) match {
                                    case Some(codeConsignresp) =>
                                      if (codeConsignresp != 0) {
                                        //consign Fail
                                        sender() ! Response(1, "Consign creation error:", None)
                                      }
                                    case None =>
                                      //consign Fail
                                      sender() ! Response(1, "Consign creation error:", None)
                                  }
                                }
                                //Successful preservation, Notify client
                                getAccount(order.accountId) match {
                                  case Some(account) =>
                                    val notifyInfo: NotifyInfo = NotifyInfo(account.email, order.id, account.userName, order.from, order.to, order.travelTime, new Date, order.seatClass, order.seatNumber, order.price)
                                    notifyService ! Preserve_success(notifyInfo,sender())
                                  case None =>
                                    sender() ! Response(1, "Account fetch  error...Notification error:", None)
                                }
                              }
                            case None =>
                              sender() ! Response(1, "order creation error:", None)
                          }

                    }
                    else{
                          sender() ! Response(1, "Trip is not Feasible:", None)
                        }
                  }
                case None =>
                  sender() ! Response(1, "Trip all detail info Error", None)
              }
            case None =>
              sender() ! Response(1, "Contacts Error", None)

          }
        }
    }


    def checkSecurity(accountId: Int): Boolean ={
      var safe: Boolean = false
      val responseFuture: Future[Any] = securityService ? Check(accountId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) safe = true
      safe
    }
    def getContactsById(contactsId: Int): Option[Contacts] ={
      var contacts: Option[Contacts] = None
      val responseFuture: Future[Any] = contactService ? FindContactsById(contactsId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) contacts = Some(response.data.asInstanceOf[Contacts])
      contacts
    }

    def getTripAllDetailInformation(gtdi: TripAllDetailInfo): Option[TripAllDetail]={
      var tripAllDetail: Option[TripAllDetail] = None
      val responseFuture: Future[Any] = travelService ? GetTripAllDetailInfo(gtdi)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) tripAllDetail = Some(response.data.asInstanceOf[TripAllDetail])
      tripAllDetail
    }

    def queryForStationId(stationName: String): Option[Int] ={
      var stationId: Option[Int] = None
      val responseFuture: Future[Any] = stationService ? QueryForIdStation(stationName)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) stationId = Some(response.data.asInstanceOf[Int])
      stationId
    }

    def dispatchSeat(date: Date, trainNumber: Int, fromStationId: Int, toStationId: Int, seatClass:Int): Option[Ticket] ={
      var ticket: Option[Ticket] = None
      val seat = Seat(date,trainNumber,fromStationId,toStationId,seatClass)
      val responseFuture: Future[Any] = seatService ? DistributeSeat(seat)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) ticket = Some(response.data.asInstanceOf[Ticket])
      ticket
    }
    def createOrder(order: Order): Option[Order] ={
      var ord: Option[Order] = None
      val responseFuture: Future[Any] = orderService ? Create(order)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) ord = Some(response.data.asInstanceOf[Order])
      ord
    }
    def addAssuranceForOrder(assuranceType:Int, orderId:Int):Option[Int]={
      var assuranceResp: Option[Int] = None
      val responseFuture: Future[Any] = assuranceService ? CreateAssurance(assuranceType,orderId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if(response.status ==0) assuranceResp = Some(response.data.asInstanceOf[Int])
      assuranceResp
    }

    def createFoodOrder(foodOrder: FoodOrder): Option[Int] ={
      var foodOrderResp: Option[Int] = None
      val responseFuture: Future[Any] = foodService ? CreateFoodOrder(foodOrder)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) foodOrderResp = Some(0)
      foodOrderResp
    }
    def createConsign(consignRequest: Consign): Option[Int] ={
      var consignResp: Option[Int] = None
      val responseFuture: Future[Any] = consignService ? InsertConsignRecord(consignRequest)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) consignResp = Some(0)
      consignResp
    }

    def getAccount(accountId: Int): Option [Account] ={
      var user: Option[Account] = None
      val responseFuture: Future[Any] = userService ? FindByUserId2(accountId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) user = Some(response.data.asInstanceOf[Account])
      user
    }

  }
}