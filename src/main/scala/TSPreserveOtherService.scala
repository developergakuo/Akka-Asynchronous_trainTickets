
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import java.util. Date

implicit val timeout: Timeout = 2.seconds

object TSPreserveOtherService {
  class PreserveOtherService extends Actor {
    var receiver: ActorRef = null
    var ticketInfoService: ActorRef = null
    var securityService: ActorRef = null
    var contactService:  ActorRef = null
    var travel2Service: ActorRef = null
    var stationService: ActorRef = null
    var seatService: ActorRef = null
    var orderOtherService: ActorRef = null
    var assuranceService: ActorRef = null
    var foodService: ActorRef = null
    var consignService: ActorRef = null
    var userService: ActorRef = null
    var notifyService: ActorRef = null

    override def receive: Receive = {
      case Preserve(oti: OrderTicketsInfo2) =>
        if (!checkSecurity(oti.accountId)) {
          sender() ! Response(1, "Security Error", None)
        }
        else {
          getContactsById(oti.contactsId) match {
            case Some(contacts) =>
              val gtdi: TripAllDetailInfo = TripAllDetailInfo(oti.tripId, oti.date, oti.from, oti.to)
              getTripAllDetailInformation(gtdi) match {
                case Some(gtdr) =>
                  val tripResponse: TripResponse = gtdr.tripResponse
                  if ((oti.seatType == SeatClass().firstClass._1) && (tripResponse.confortClass == 0))
                    sender() ! Response(1, "First class seats not enough", None)
                  else if ((tripResponse.economyClass == SeatClass().secondClass._1) && (tripResponse.confortClass == 0))
                    sender() ! Response(1, "Economy class seats not enough", None)
                  else {
                    val trip: Trip = gtdr.trip
                    val fromStationId: Int = queryForStationId(oti.from).get
                    val toStationId: Int = queryForStationId(oti.to).get
                    val order: Order = Order(id = scala.util.Random(100000), boughtDate = new Date(),
                      status = OrderStatus().NOTPAID._1, contactsDocumentNumber = contacts.documentNumber,
                      documentType = contacts.documentType, contactsName = contacts.name, from = fromStationId,
                      to = toStationId, trainNumber = oti.tripId, accountId = oti.accountId, seatClass = oti.seatType,
                      travelDate = oti.date, travelTime = gtdr.tripResponse.startingTime)
                    val query: Travel = Travel(trip, oti.from, oti.to, new Date())
                    val response: Future[Any] = ticketInfoService ? QueryForTravel(query)
                    response onComplete {
                      case Success(resp) =>
                        if (resp.asInstanceOf[Response].status == 0) {
                          val resultForTravel: TravelResult = resp.asInstanceOf[Response].data.asInstanceOf[TravelResult]
                          if (oti.seatType == SeatClass().firstClass._1) { //Dispatch the seat
                            val ticket: Ticket = dispatchSeat(oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().firstClass._1).get
                            order.seatNumber = ticket.seatNo
                            order.seatClass = SeatClass().firstClass._1
                            order.price = resultForTravel.prices.get("comfortClass").get
                          }
                          else {
                            val ticket: Ticket = dispatchSeat(oti.date, order.trainNumber, fromStationId, toStationId, SeatClass().secondClass._1).get
                            order.seatNumber = ticket.seatNo
                            order.seatClass = SeatClass().secondClass._1
                            order.price = resultForTravel.prices.get("economyClass").get
                          }


                          createOrder(order) match {
                            case Some(ord) =>
                              //5.检查保险的选择
                              if (!(oti.assurance == 0)) { //insure
                                addAssuranceForOrder(oti.assurance, ord.id) match {
                                  case Some(int) =>
                                    if (int == 0) {

                                    } else {

                                    }

                                  case None =>

                                }
                                //6.增加订餐
                                //            System.out.println("[Food Service]!!!!!!!!!!!!!!!foodstorename=" + oti.getStationName()+"   "+oti.getStoreName());
                                if (oti.foodType != 0) {
                                  val foodOrder: FoodOrder = FoodOrder(orderId = ord.id, foodType = oti.foodType, price = oti.foodPrice, foodName = oti.foodName)
                                  if (oti.foodType == 2) {
                                    foodOrder.stationName = oti.stationName
                                    foodOrder.storeName = oti.storeName
                                  }
                                  createFoodOrder(foodOrder) match {
                                    case Some(code) =>
                                      if (code == 0) {

                                      } else {

                                      }

                                    case None =>
                                    // buy food fail

                                  }
                                }
                                else {
                                  //[Preserve Service][Step 6] Do not need to buy food"
                                }

                                //7.增加托运
                                if (null != oti.consigneeName && !("" == oti.consigneeName)) {
                                  val consignRequest: Consign = Consign(orderId = ord.id, accountId = ord.accountId,
                                    handleDate = oti.handleDate, targetDate = ord.travelDate, from = ord.from, to = ord.to,
                                    consignee = oti.consigneeName, phone = oti.consigneePhone, weight = oti.consigneeWeight,
                                    isWithin = oti.isWithin)
                                  createConsign(consignRequest) match {
                                    case Some(codeConsignresp) =>
                                      if (codeConsignresp == 0) {

                                      }

                                    case None =>
                                    //consign Fail
                                  }
                                }

                                else {
                                  //"[Preserve Service][Step 7] Do not need to consign")
                                }

                                //8.发送notification

                                getAccount(order.accountId) match {
                                  case Some(account) =>
                                    val notifyInfo: NotifyInfo = NotifyInfo(account.email, order.id, account.userName, order.from, order.to, order.travelTime, new Date, order.seatClass, order.seatNumber, order.price)


                                    sendEmail(notifyInfo)
                                  case None =>


                                }
                              }
                            case None =>
                              sender() ! Response(1, "order creation error:", None)
                          }
                        }


                      case Failure(exception) =>
                        sender() ! Response(1, exception.toString,None)

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
      val response: Future[Any] = securityService ? Check(accountId)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) safe = true
        case Failure(_) =>
          safe = false
      }
      safe
    }
    def getContactsById(contactsId: Int): Option[Contacts] ={
      var contacts: Option[Contacts] = None
      val response: Future[Any] = contactService ? FindContactsById(contactsId)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) contacts = Some(resp.data.asInstanceOf[Contacts])
        case Failure(_) =>
          contacts = None
      }
      contacts
    }

    def getTripAllDetailInformation(gtdi: TripAllDetailInfo): Option[TripAllDetail]={
      var tripAllDetail: Option[TripAllDetail] = None
      val response: Future[Any] = travel2Service ? GetTripAllDetailInfo(gtdi)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) tripAllDetail = Some(resp.data.asInstanceOf[TripAllDetail])
        case Failure(_) =>
          tripAllDetail = None
      }
      tripAllDetail

    }
    def queryForStationId(stationName: String): Option[Int] ={
      var stationId: Option[Int] = None
      val response: Future[Any] = stationService ? QueryForIdStation(stationName)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) stationId = Some(resp.data.asInstanceOf[Int])
        case Failure(_) =>
          stationId = None
      }
      stationId
    }
    def dispatchSeat(date: Date, trainNumber: Int, fromStationId: Int, toStationId: Int, seatClass:Int): Option[Ticket] ={
      var ticket: Option[Ticket] = None
      val seat = Seat(date,trainNumber,fromStationId,toStationId,seatClass)
      val response: Future[Any] = seatService ? DistributeSeat(seat)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) ticket = Some(resp.data.asInstanceOf[Ticket])
        case Failure(_) =>
          ticket = None
      }
      ticket
    }
    def createOrder(order: Order): Option[Order] ={
      var ord: Option[Order] = None
      val response: Future[Any] = orderOtherService ? Create(order)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) ord = Some(resp.data.asInstanceOf[Order])
        case Failure(_) =>
          ord = None
      }
      ord

    }
    def addAssuranceForOrder(assuranceType:Int, orderId:Int):Option[Int]={
      var assuranceResp: Option[Int] = None
      val response: Future[Any] = assuranceService ? CreateAssurance(assuranceType,orderId)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) assuranceResp = Some(0)
        case Failure(_) =>
          assuranceResp = None
      }
      assuranceResp

    }

    def createFoodOrder(foodOrder: FoodOrder): Option[Int] ={
      var foodOrderResp: Option[Int] = None
      val response: Future[Any] = foodService ? CreateFoodOrder(foodOrder)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) foodOrderResp = Some(0)
        case Failure(_) =>
          foodOrderResp = None
      }
      foodOrderResp
    }
    def createConsign(consignRequest: Consign): Option[Int] ={
      var consignResp: Option[Int] = None
      val response: Future[Any] = consignService ? InsertConsignRecord(consignRequest)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) consignResp = Some(0)
        case Failure(_) =>
          consignResp = None
      }
      consignResp

    }

    def getAccount(accountId: Int): Option [UserDto] ={
      var user: Option[UserDto] = None
      val response: Future[Any] = userService ? FindByUserId2(accountId)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) user = Some(resp.data.asInstanceOf[UserDto])
        case Failure(_) =>
          user = None
      }
      user

    }
    def sendEmail(notifyInfo: NotifyInfo): Option[Int] ={
      var sendEmailResp: Option[Int] = None
      val response: Future[Any] = notifyService ? Preserve_success(notifyInfo)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) sendEmailResp = Some(0)
        case Failure(_) =>
          sendEmailResp = None
      }
      sendEmailResp

    }

  }
}