package TSCommon

import java.util.Date

import TSCommon.Commons.Evt
import akka.actor.ActorRef
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.duration.Duration


object Commons {
  val duration: Duration = 20.seconds
 implicit val timeout: Timeout = 20.seconds

  trait Cmd
  trait Evt
  case class Response (status: Int, msg: String, data: Any)
  case class User(userId: Int, username: String, password: String)
  case class TrainType(id: Int, economyClass: Int, comfortClass: Int, averageSpeed: Int)
  case class Route(id: Int, stations: List[Int], distances: List[Int],startStationId: Int,terminalStationId: Int )
  case class Station(id: Int, name: String, stayTime: Int)
  case class AdminTrip(trip: Trip, trainType: TrainType, route: Route)
  case class Trip( tripId: Int,trainTypeId: Int, routeId: Int, startingTime: Date, startingStationId: Int, stationsId: List[Int], terminalStationId: Int)
  case class Seat (var travelDate: Date = null, var trainNumber: Int = -1, var startStation: Int = -1, var destStation: Int = -1, var seatType: Int = -1)
  case class TravelInfo (tripId: Int, trainTypeId: Int, routeId: Int, startingStationId: Int, stationsId: List[Int], terminalStationId: Int, startingTime: Date, endTime: Date)
  case class TravelResult(status: Boolean, percent: Double, trainType: TrainType, prices: Map[String,Double], message: String)
  case class Ticket (seatNo: Int, startStation: Int, destStation: Int)
 //food
  case class AllTripFood (trainFoodList: List[TrainFood], foodStoreListMap: Map[String, List[FoodStore]])
  case class TrainFood(id: Int, tripId: Int,foodList: List[Food])
  case class FoodStore(var id: Int, stationId: Int, storeName: String, telephone: String, businessTime: (Date,Date), deliveryFee: Double, foodList: List[Food])
  case class FoodOrder (var id: Int = -1, orderId: Int, foodType: Int, var stationName: String = "",var  storeName: String = "", var foodName: String = "", price:Double)
  case class Food(foodName: String, price: Double)
  case class  AuthDto (userId: Int, userName: String, password: String)
  case class TransferTravelInfo( fromStationName: String, viaStationName: String, toStationName: String, travelDate: Date, trainType: TrainType)
  case class TransferTravelResult ( firstSectionResult: List[TripResponse], secondSectionResult: List[TripResponse])
  case class Travel (trip: Trip, startingPlace: String,endPlace: String, departureTime: Date)
  case class  RoutePlanResultUnit(tripId: Int, trainTypeId: Int, fromStationName: String, toStationName: String, stopStations: List[Int], priceForSecondClassSeat: Double, priceForFirstClassSeat: Double, startingTime: Date, endTime: Date)
  case class  TravelAdvanceResultUnit(tripId: Int, trainTypeId: Int, fromStationName: String, toStationName: String, stopStations: List[String], priceForSecondClassSeat: Double, numberOfRestTicketSecondClass: Int, priceForFirstClassSeat: Double, numberOfRestTicketFirstClass: Int)
  case class  RoutePlanInfo (fromStationName: String, toStationName: String, travelDate: Date, num:Int)
  case class  SecurityConfig(id: Int, name: String, value: Int, description: String)
  case class Config(name: String, value: Double, description : String)
  case class SeatClass(
    none:(Int,String) =(0, "NoSeat"),
    business:(Int,String)= (1, "GreenSeat"),
    firstClass:(Int,String)= (2, "FirstClassSeat"),
    secondClass:(Int,String)= (3, "SecondClassSeat"),
    hardSeat :(Int,String)= (4, "HardSeat"),
    softSeat:(Int,String) = (5, "SoftSeat"),
    hardBed :(Int,String)= (6, "HardBed"),
    softBed:(Int,String)=(7, "SoftBed"),
    highSoftBed:(Int,String)= (8, "HighSoftSeat"))
  case class SoldTicket(var  travelDate: Date,
                        var trainNumber: Int,
                        var noSeat: Int= 0,
                        var businessSeat: Int= 0,
                        var firstClassSeat: Int= 0,
                        var secondClassSeat: Int= 0,
                        var hardSeat: Int= 0,
                        var softSeat : Int= 0,
                        var hardBed : Int= 0,
                        var softBed : Int= 0,
                        var highSoftBed : Int= 0)
  case class TripAllDetailInfo (tripId: Int, travelDate: Date, from: String, to: String)


  case class ConsignPrice(id: Int,index: Int, initialWeight: Double, initialPrice: Double,withinPrice: Double, beyondPrice: Double)

  case class TripAllDetail(tripResponse: TripResponse, trip: Trip)
  case class  TripInfo (startingPlace: String, endPlace: String, departureTime: Date)
  case class TripResponse (var tripId: Int = -1,
                           var  trainTypeId: Int = -1,
                           var startingStation:String = "",
                           var terminalStation: String = "",
                           var startingTime: Date = null,
                           var endTime: Date = null,
                           var economyClass: Int = 0,
                           var confortClass: Int =0,
                           var priceForEconomyClass: Double = 0,
                           var priceForConfortClass: Double =0)

  case class RouteInfo(routeId: Int, startStation: Int, endStation: Int, stationList: List[Int], distanceList: List[Int])
  case class  PriceConfig(id: Int, trainType: Int, routeId: Int, basicPriceRate: Double, firstClassPriceRate: Double)

 case  class Order(var id: Int, boughtDate: Date, travelDate: Date, travelTime: Date, accountId: Int, contactsName: String, var documentType: Int = -1, var contactsDocumentNumber: Int = -1, var trainNumber: Int = -1, var coachNumber: Int = -1, var seatClass: Int = -1, var seatNumber: Int = -1, var from: Int = -1, var to: Int = -1, var status: Int = -1, var price: Double = 0.0)
 case class OrderTicketsInfo (contactsId: Int, tripId: Int, seatType: Int, loginToken: String, accountId: Int, date: Date, from: String, to: String, assurance: Int)


 case class OrderTicketsInfo2(
    accountId: Int,
    contactsId: Int,
    tripId: Int,
    seatType : Int,
    date: Date,
    from: String,
    to: String,
    assurance : Int,
  //food
    foodType : Int,
    stationName : String,
    storeName: String,
    foodName : String,
    foodPrice : Double,
  //consign
    handleDate: Date,
    var consigneeName: String= "",
    var consigneePhone: String = "",
    consigneeWeight: Double,
    isWithin: Boolean
  )

  case class PaymentDifferenceInfo(orderId: Int, tripId : Int, userId: Int, price: Double)
 case class PaymentInfo( userId: Int, orderId: Int, tripId: Int, price: Double)
 case class Payment (var Id: Int = -1, orderId: Int, userId: Int, price: Double)
 case class Payment2 (var Id: Int = -1, orderId: Int, userId: Int, price: Double, var paymentType: (String, Int) = null)
 final case class PaymentType (P:(String,Int) =  ("Payment",1), D:(String,Int)=("Difference",2),O:(String,Int)=("Outside Payment",3),E:(String,Int)=("Difference & Outside Payment",4))
  case class RebookInfo (loginId: Int, orderId: Int, oldTripId: Int, tripId: Int, seatType: Int, date: Date)
  case class VerifyResult (status: Boolean, message: String)
 //case class UserDto (userName: String, password: String, gender: Int, documentType: Int, documentNum: Int, email: String)
 case class OrderStatus (NOTPAID:(Int,String) =   (0,"Not Paid"), PAID: (Int,String)    =  (1,"Paid & Not Collected"), COLLECTED: (Int,String)  = (2,"Collected"), CHANGE: (Int,String)   =   (3,"Cancel & Rebook"), CANCEL: (Int,String)   =   (4,"Cancel"), REFUNDS: (Int,String)  =  (5,"Refunded"), USED: (Int,String)     =   (6,"Used"))
 case class Gender (NONE:(Int,String) =   (0,"Null"), MALE: (Int,String)    =  (1,"Male"), FEMALE: (Int,String)  = (2,"Female"), OTHER: (Int,String)   =   (3,"Other"))
 case class Account(userId: Int, userName:String, password: String, gender: String, documentType: Int, documentNum: Int, email: String)
 case class OrderInfo (loginId: Int, travelDateStart: Date, travelDateEnd: Date, boughtDateStart: Date, boughtDateEnd: Date, state: Int, enableTravelDateQuery: Boolean, enableBoughtDateQuery: Boolean, enableStateQuery: Boolean)
 case  class PriceInfo(id:Int, trainType: Int, routeId: Int, basicPriceRate: Double, firstClassPriceRate: Double)
 case class QueryInfo(loginId: Int, travelDateStart: Date, travelDateEnd: Date, boughtDateStart: Date, boughtDateEnd: Date, state: Int, enableTravelDateQuery: Boolean, enableBoughtDateQuery: Boolean,enableStateQuery: Boolean)
 case  class Mail ( mailFrom: String, mailto: String, var mailCc: String = "",var mailBcc: String = "", mailSubject: String, var mailContent: String ="", var contentType: String ="", var attachments: List[Any] = List(), model: Map[String,Any])
 case class NotifyInfo(email: String, orderNumber: Int, username: String, startingPlace: Int, endPlace: Int, startingTime: Date, date: Date, seatClass: Int, seatNumber: Int, price: Double)
 case class OrderAlterInfo (accountId: Int, previousOrderId: Int, loginToken: String, newOrderInfo: Order)
 case class Assurance( id: Int, orderId: Int, assurance: (Int,String, Double))
 case class AssuranceTypeBean(index: Int, name: String, price: Double)
 case class PlainAssurance(id: Int, oderId: Int, typeIndex: Int, typeName: String, typePrice: String)
 case class AssuranceType(assuranceTypes: List[(Int,String, Double)]= List((1, "Traffic Accident Assurance", 3.0)))
 case class Consign(var id: Int = -1, orderId:Int, accountId: Int, handleDate: Date, targetDate: Date,from: Int, to: Int, consignee: String, phone: String, weight: Double, isWithin: Boolean)


  case class ConsignRecord(id: Int, orderId: Int , accountId: Int , handleDate: Date , targetDate: Date,from: Int , to: Int , consignee: String, phone: String, weight: Double , price: Double)
  case class OrderTicketsResult(status: Boolean,message: String,order: Order)

  class InsertConsignRecordResult ( status: Boolean,  message: String)

  case class GetPriceDomain (  weight: Double, isWithinRegion: Boolean)


  case class Money (userId: Int, money: Double)
 case class Money2 (userId: Int, money: Double, var moneyType: (String, Int) = null)

  case class AccountInfo (userId: Int, money: Double)



 case class Balance ( userId: Int,  balance: Double)
 final case class MoneyType (A: (String,Int)=("Add Money",1),D: (String,Int)=("Draw Back Money",2))
 case class Contacts(id: Int, accountId: Int, name: String, documentType: Int, documentNumber: Int, phoneNumber: String)
  case class OrderSecurity (orderNumInLastOneHour: Int,orderNumOfValidOrder: Int)
  case class LeftTicketInfo(soldTickets: List[Ticket])
  case class DocumentType(NONE: (Int,String) =   (0,"Null"), ID_CARD: (Int,String)  = (1,"ID Card"), PASSPORT:   (Int,String) =  (2,"Passport"), OTHER:  (Int,String) =      (3,"Other"))


  case class OutsidePaymentInfo(
     orderId: Int,
     price: Double,
     userId: Int,
  )


  case class SearchCheapestResult(info: RoutePlanInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

  case class SearchQuickestResult(info: RoutePlanInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

  case class  SearchMinStopStations(info: RoutePlanInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt



  final class Type (G: (String,Int)=("G", 1), D:(String,Int)=("D", 2), Z:(String,Int)=("Z", 3), T:(String,Int)=("T", 4), K:(String,Int)=("K", 5))
  //travel commands
  case class CreateTravel(travelInfo: TravelInfo) extends Evt
  case class RetrieveTravel(tripId: Int) extends Evt
  case class UpdateTravel(travelInfo: TravelInfo) extends Evt
  case class DeleteTravel(tripId: Int) extends Evt
  case class QueryTravel(deliveryId: Long,requester: ActorRef, RequestId: Int, tripInfo: TripInfo,var trip_route: List[(Trip, Route)] = List()) extends Evt
  case class QueryTravelComplete(requester: ActorRef,RequestId: Int) extends Evt
  case class GetTripAllDetailInfo(deliveryId: Long, requester: ActorRef, requestId: Int,gtdi: TripAllDetailInfo, var temRoute: Option[Route] = None,var tempTrip: Option[Trip]= None,var label: String = "",var sender: Option[ActorRef] =None ) extends Evt
 case class ResponseGetTripAllDetailInfo(deliveryId: Long, requester: ActorRef, requestId: Int,gtdr: TripAllDetail, found: Boolean,var label: String = "",var sender: Option[ActorRef] =None) extends Evt
  case class GetRouteByTripId(tripId: Int) extends Evt
  case class GetTrainTypeByTripId(tripId: Int) extends Evt
  case class QueryAllTravel() extends Evt
  case class  GetTripByRoute(routeIds: List[Int]) extends Evt
  case class AdminQueryAll() extends Evt

  case class GetTrainType(trainTypeId: Int) extends Evt
  case class QueryForStationId(stationName: String) extends Evt

 case class QueryForTravel(deliveryID: Long,requester: ActorRef,requestId: Int,travel: Travel, var requestLabel: String = "", var label: String = "",var sender: Option[ActorRef] =None ) extends  Evt
 case class ResponseQueryForTravel(deliveryID: Long,requester: ActorRef,requestId: Int,travel: TravelResult, found: Boolean,var requestLabel: String = "",var label: String = "",var sender: Option[ActorRef] =None , var tripId: Int = -1) extends  Evt

  //AuthService commands
  case class DeleteUserByUserId(userId: Int) extends Evt
  case class GetLeftTicketOfInterval(seat: Seat) extends Evt
  case class GetSoldTickets(seat: Seat) extends Evt
  case class SaveUser(user:User) extends Evt

  //userService
  case class SaveUserDto (userDto: Account) extends Evt
  case class GetAllUsers() extends Evt
  case class  FindByUserName(userName: String)  extends Evt
  case class  FindByUserName2(userName: String)  extends Evt
  case class FindByUserId(userId: Int)  extends Evt
 case class FindByUserId2(deliverId: Long, requester: ActorRef,requestId: Int,userId: Int)  extends Evt
 case class ResponseFindByUserId2(deliverId: Long, requester: ActorRef,requestId: Int,account: Option[Account])  extends Evt
  case class DeleteUser(userId: Int)  extends Evt
  case class  UpdateUser(user: Account)  extends Evt
  case  class CreateDefaultAuthUser(dto: AuthDto) extends Evt
  case class DeleteUserAuth(userId: Int) extends Evt

 //route service
  case class GetRouteByStartAndTerminal(startId: Int, terminalId: Int) extends Evt
  case class GetAllRoutes() extends Evt
  case class GetRouteById(routeId: Int) extends Evt
  case class DeleteRoute(routeId: Int) extends Evt
  case class CreateAndModify(routeInfo: RouteInfo) extends Evt

  //train service
  case class CreateTrain(trainType: TrainType) extends Evt
  case class RetrieveTrain(id: Int) extends Evt
  case class UpdateTrain(trainType: TrainType) extends Evt
  case class DeleteTrain(id: Int) extends Evt
  case class QueryTrains( ) extends Evt

  //station service
  case class CreateStation(station: Station) extends Evt

  case class ExistStation(stationName: String) extends Evt

  case class UpdateStation(station: Station) extends Evt

  case class DeleteStation(station: Station) extends Evt

  case class QueryStations() extends Evt

 case class QueryForIdStation(deliveryId: Long, requester: ActorRef, requestId: Int,stationName: String, toOrFRom: Int) extends Evt
 case class ResponseQueryForIdStation(deliveryId: Long, requester: ActorRef, requestId: Int,stationId: Int,found: Boolean, toOrFRom: Int) extends Evt

  case class QueryForIdBatchStation(nameList: List[String]) extends Evt

  case class QueryByIdStation(stationId: Int) extends Evt

  case class QueryByIdBatchStation(stationIdList: List[Int]) extends Evt

  //Price Service
  case class QueryPriceConfigByRouteIdAndTrainType(routeId: Int, trainType: Int) extends Evt

// seat service
  case class DistributeSeat(deliveryId: Long, requester: ActorRef, requestId: Int, seat: Seat, var label: String = "") extends Evt
 case class ResponseDistributeSeat(deliveryId: Long, requester: ActorRef, requestId: Int, seatClass: Int, ticket: Ticket, found: Boolean,var label: String = "") extends Evt

 //case class GetLeftTicketOfInterval(seatRequest: Seat) extends Evt
  // travelPlanService
  case class GetTransferSearch(trasnferTravelInfo: TransferTravelInfo,var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
  case class GetCheapest(tripInfo: TripInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

  case class GetQuickest(tripInfo: TripInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

  case class GetMinStation(tripInfo: TripInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

  case class GetRestTicketNumber(travelDate: Date, trainNumber: Int, startStationName: String, endStationName: String, seatType: Int)


 case class GetAccountByIdInfo(accountId:Int)
  case class GetAccountByIdResult(status: Boolean, message: String, account: Account)
  case class GetOrderByIdInfo(orderID: Int)

  //security
  case class FindAllSecurityConfig() extends Evt

  case class AddNewSecurityConfig(info: SecurityConfig) extends Evt

  case class ModifySecurityConfig(info: SecurityConfig) extends Evt

  case class DeleteSecurityConfig(id: Int) extends Evt

 case class Check(requester: ActorRef, requestId: Int, deliveryId: Long, accountId: Int) extends Evt
 case class SecurityCheckResponse(requester: ActorRef, requestId: Int, deliveryId: Long, isSecure: Boolean, accountId:Int) extends Evt

  //order

  case class FindOrderById(id: Int, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="",var sourceLabel:String =""  ) extends Evt

 case class ResponseFindOrderById(order: Order, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="" , var sourceLabel:String ="", var found: Boolean = false ) extends Evt


 case class Create(deliveryId: Long, requester: ActorRef,requestId: Int,newOrder: Order,var requestLabel:String ="") extends Evt
 case class ResponseCreate(deliveryId: Long, requester: ActorRef,requestId: Int,newOrder: Order, created: Boolean,var requestLabel:String ="") extends Evt

  case class SaveChanges(order: Order) extends Evt

  case class CancelOrder(accountId: Int, orderId: Int, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponseCancelOrder(canceled: Boolean, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt



 case class QueryOrders(qi: OrderInfo, accountId: Int) extends Evt

  case class QueryOrdersForRefresh(qi: OrderInfo, accountId: Int) extends Evt

  case class AlterOrder(oai: OrderAlterInfo) extends Evt

  case class QueryAlreadySoldOrders(travelDate: Date, trainNumber: Int, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="",var label: String = "" , var sender:Option[ActorRef] = None , var tripId: Int = -1 ) extends Evt

 case class ResponseQueryAlreadySoldOrders(soldTicket: SoldTicket, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="", var found: Boolean = false,var label: String = "",var sender:Option[ActorRef] = None,var tripId: Int = -1) extends Evt
  case class GetAllOrders() extends Evt

  case class ModifyOrder(orderId: Int, status: Int) extends Evt

  case class GetOrderPrice(orderId: Int) extends Evt

  case class PayOrder(orderId: Int) extends Evt

  case class GetOrderById(orderId: Int,  var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponseGetOrderById(order: Order,  var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="" , var found: Boolean= false, var sourceLabel: String = "") extends Evt


 case class CheckSecurityAboutOrder(dateFrom: Date, accountId: Int) extends Evt

  case class InitOrder(order: Order) extends Evt

  case class DeleteOrder(orderId: Int, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponseDeleteOrder(deleted: Boolean, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt



 //case class GetSoldTickets(seatRequest: Seat) extends Evt

  case class AddNewOrder(order: Order) extends Evt

  case class UpdateOrder(order: Order, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponseUpdateOrder(var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String ="" , var update: Boolean =  false) extends Evt



 //config service
  case class CreateConfig(info: Config) extends Evt

  case class Update(info: Config) extends Evt
 case class Update2(index: Int,info: Config) extends Evt

  case class Query(name: String) extends Evt

  case class Delete(name: String) extends Evt

  case class QueryAll() extends Evt

  case class DeleteConfig2(index: Int) extends Evt
 //Rebook
 case class Rebook(info: RebookInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

 case class PayDifference(info: RebookInfo, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

 // inside service
 case class Pay( info: PaymentInfo, var deliverID: Long = 0) extends Evt
 case class CreateAccount(info: AccountInfo) extends Evt
 case class AddMoney(deliveryId: Long, userId: Int,  money: Double) extends Evt
  case class  MoneyReceived(deliveryId: Long)



  case class QueryPayment( ) extends Evt
 case class QueryAccount( ) extends Evt
 case class DrawBack( userId: Int,  money: Double, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponseDrawBack(drawnBack: Boolean, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

 case class PayDifference2( info: PaymentInfo,var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt
 case class ResponsePayDifference2( paid: Boolean,var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

 case class QueryAddMoney( ) extends Evt
 case class SavePayment(payment: Payment2) extends Evt
 
 //price Service
 case class CreateNewPriceConfig(priceConfig: PriceConfig) extends Evt
 case class FindById(id: Int) extends Evt
 case class FindByRouteIdAndTrainType(routeId: Int, trainType: Int) extends Evt
 case class FindAllPriceConfig() extends Evt
 case class DeletePriceConfig(priceConfig: PriceConfig) extends Evt
 case class UpdatePriceConfig(priceConfig: PriceConfig) extends Evt
 
 //payment service
 //case class Pay(info: Payment) extends Evt
 case class AddMoney2(payment: Payment) extends Evt
 case class Query2() extends Evt
 case class InitPayment(payment: Payment) extends Evt

 //admin service
 case class GetAllContacts() extends Evt

 case class AddContact(contact: Contacts) extends Evt

 case class DeleteContact(contactsId: Int) extends Evt

 case class ModifyContact(mci: Contacts) extends Evt

 ////////////////////////////station///////////////////////////////
 case class GetAllStations() extends Evt

 case class AddStation(s: Station) extends Evt

 //case class DeleteStation(s: Station) extends Evt

 case class ModifyStation(s: Station) extends Evt

 ////////////////////////////train///////////////////////////////
 case class GetAllTrains() extends Evt

 case class AddTrain(t: TrainType) extends Evt

 //case class DeleteTrain(id: Int) extends Evt

 case class ModifyTrain(t: Nothing) extends Evt

 ////////////////////////////config///////////////////////////////
 case class GetAllConfigs() extends Evt

 case class AddConfig(c: Config) extends Evt

 case class DeleteConfig(name: String) extends Evt

 case class ModifyConfig(c: Config) extends Evt

 ////////////////////////////price///////////////////////////////
 case class GetAllPrices() extends Evt

 case class AddPrice(pi: PriceInfo) extends Evt

 case class DeletePrice(pi: PriceInfo) extends Evt

 case class ModifyPrice(pi: PriceInfo) extends Evt

// case class GetAllOrders() extends Evt

 case class DeleteOrder2(orderId: Int, trainNumber: Int) extends Evt

 case class UpdateOrder2(request: Order) extends Evt

 case class AddOrder(request: Order) extends Evt

 //case class GetAllRoutes() extends Evt

 case class CreateAndModifyRoute(RouteInfo: Nothing) extends Evt

 //case class DeleteRoute(routeId: Int) extends Evt

 case class GetAllTravels() extends Evt

 case class AddTravel(request: TravelInfo) extends Evt

 //case class UpdateTravel(request: TravelInfo) extends Evt

 //case class DeleteTravel(tripId: Int) extends Evt
 //case class GetAllUsers() extends Evt

 //case class DeleteUser(userId: Int) extends Evt

 //case class UpdateUser(userDto: UserDto) extends Evt

 case class AddUser(userDto: Account) extends Evt

 //case class FindAssuranceById(id: Int) extends Evt

// case class FindAssuranceByOrderId(orderId: Int) extends Evt

 case class CreateAssurance(deliveryId: Long,requester: ActorRef,requestId: Int,typeIndex: Int, orderId: Int) extends Evt
 case class ResponseCreateAssurance(deliveryId: Long,requester: ActorRef,requestId: Int,created: Boolean) extends Evt
 case class CreateAssurance2( orderId: Int, assuranceType: (Int,String, Double)) extends Evt

 //case class DeleteById(assuranceId: Int) extends Evt

 //case class DeleteByOrderId(orderId: Int) extends Evt

 //case class Modify(assuranceId: Int, orderId: Int, typeIndex: Int) extends Evt

 //case class GetAllAssurances() extends Evt

 //case class GetAllAssuranceTypes() extends Evt
 case class FindAssuranceById(id: Int) extends Evt

 case class FindAssuranceByOrderId(orderId: Int) extends Evt

 //case class Create(typeIndex: Int, orderId: String) extends Evt

 case class DeleteById(assuranceId: Int) extends Evt

 case class DeleteByOrderId(orderId: Int) extends Evt

 case class Modify(assuranceId: Int, orderId: Int, typeIndex: Int) extends Evt

 case class GetAllAssurances() extends Evt

 case class GetAllAssuranceTypes() extends Evt

 // cancel service
 case class CalculateRefund(orderId: Int,  var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt

 case class CancelOrder2(orderId: Int, var deliveryId: Long = 0, var requester: ActorRef = null, var requestId: Int = -1, var requestLabel:String =""  ) extends Evt


 //Notification service
 case class SendNotification(notifyInfo: NotifyInfo) extends Evt

 // Consignprice service
 case class GetPriceByWeightAndRegion(weight: Double, isWithinRegion: Boolean) extends Evt

 case class QueryPriceInformation() extends Evt

 case class CreateAndModifyPrice(config: ConsignPrice) extends Evt

 case class GetPriceConfig() extends Evt

 // consign service

 case class InsertConsignRecord(deliverId: Long, requester: ActorRef,requestId: Int, consignRequest: Consign)  extends Evt
 case class ResponseInsertConsignRecord(deliverId: Long, requester: ActorRef,requestId: Int, created: Boolean)  extends Evt

 case class InsertConsignRecord2(consignRecord: ConsignRecord)  extends Evt

 case class UpdateConsignRecord(consignRequest: Consign)  extends Evt

 case class QueryByAccountId(accountId: Int)  extends Evt

 case class QueryByOrderId(orderId: Int)  extends Evt

 case class QueryByConsignee(consignee: String)  extends Evt

 //Contacts service
 case class FindContactsById(deliveryId: Long, requester: ActorRef, requestNo: Int,id: Int)  extends Evt
 case class ContactResponse(deliveryId: Long, requester: ActorRef,requestId: Int, found: Boolean, contacts: Option[Contacts]) extends Evt

 case class FindContactsByAccountId(accountId: Int)  extends Evt


// FoodMap service
 case class CreateFoodStore(fs: FoodStore ) extends Evt

 case class CreateTrainFood(tf: TrainFood ) extends Evt

 case class ListFoodStores( ) extends Evt

 case class ListTrainFood( ) extends Evt

 case class ListFoodStoresByStationId(stationId: Int ) extends Evt

 case class ListTrainFoodByTripId(tripId: Int ) extends Evt

 case class GetFoodStoresByStationIds(stationIds: List[Int] ) extends Evt
 
 // food service 
 case class CreateFoodOrder(deliverId: Long, requester: ActorRef,requestId: Int,fs: FoodOrder ) extends Evt
 case class ResponseCreateFoodOrder(deliverId: Long, requester: ActorRef,requestId: Int, created: Boolean ) extends Evt

 case class DeleteFoodOrder(orderId: Int) extends Evt

 case class FindByOrderId(orderId: Int) extends Evt

 case class UpdateFoodOrder(updateFoodOrder: FoodOrder) extends Evt

 case class FindAllFoodOrder() extends Evt

 case class GetAllFood(date: Date, startStation: String, endStation: String, tripId: Int) extends Evt
 
//Notification service

 case class Preserve_success(deliveryId: Long, requester: ActorRef, requestId: Int, info: NotifyInfo,receiver: ActorRef ) extends Evt
 case class Order_create_success(info: NotifyInfo,receiver: ActorRef ,deliveryId: Long, requestId: Int ) extends Evt
 case class Order_changed_success(info: NotifyInfo,receiver: ActorRef, deliveryId: Long, requestId: Int ) extends Evt
 case class Order_cancel_success(info: NotifyInfo,receiver: ActorRef, deliveryId: Long, requestId: Int ) extends Evt
 case class  Order_Rebook_success(info: NotifyInfo,receiver: ActorRef,deliveryId: Long, requestId: Int ) extends Evt
 case class Order_Paid_success(info: NotifyInfo,receiver: ActorRef, deliveryId: Long, requestId: Int ) extends Evt
 case class SaveMail(user: ActorRef,email: Mail) extends Evt
 case class ConfirmMailDelivery(deliverId: Long) extends Cmd
  case class RequestComplete(deliveryId: Long, Requester: ActorRef, requestId: Int)  extends Evt
 // preserve service
 case class Preserve(deliveryId: Long, actorRef: ActorRef, orderNumber: Int, oti: OrderTicketsInfo2) extends Evt

  case class SeatServiceId()
 //Execute service
 case class TicketExecute(orderId: Int) extends Evt
 case class TicketCollect(orderId: Int) extends Evt

  //Client
  case class ClientLogin(userName: String, password: String) extends Evt
  case class ClientMakeOrder()extends Evt
  case class ClientModifyOrder()extends Evt
  case class ClientCancelOrder(order: Order)extends Evt
  case class ClientRebook(oldOrder: Order, newTrip: Trip) extends Evt
  case class ClientPay(order: Order) extends Evt
  case class ClientPreserve(startingStaion: String, endSation: String, travelDate: Date, seatsCount: Int, seatType: Int, assuranceType: Int =1, foodType: Int = 1) extends Evt
  case class PreservationSuccess(deliveryId: Long, mail: Mail) extends Evt
  case class OrderCreated(deliveryId: Long, mail: Mail) extends Evt
  case class OrderChanged(deliveryId: Long, mail: Mail) extends Evt
  case class OrderCanceled(deliveryId: Long, mail: Mail) extends Evt
  case class OrderRebooked(deliveryId: Long, mail: Mail) extends Evt
  case class OrderPaid(deliveryId: Long, mail: Mail) extends Evt


 case class RebookDelivered(deliveryId: Long)
 case class PaymentDelivered(deliveryId: Long)
 case class OrderDelivered(deliveryId: Long)
 case class CancelOrderDelivered(deliveryId: Long)
 case class PreservationDelivered(deliveryId: Long)






}
