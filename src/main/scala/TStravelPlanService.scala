import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global

implicit val timeout: Timeout = 2.seconds
import scala.util.{Success, Failure}
import java.util.Date

object TStravelPlanService {

  trait TravelPlanService {

  }

  case class UserDtoRepository(users: Map[Int, UserDto])

  class UserService extends PersistentActor {
    var state = UserDtoRepository(users = Map())
    var authService: ActorRef = null
    var ticketInfoService: ActorRef = null
    var stationService: ActorRef = null
    var travel2service: ActorRef = null
    var travelService: ActorRef = null
    var seatService: ActorRef = null
    var routePlanService: ActorRef = null


    override def preStart(): Unit = {
      println("UserService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("UserService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "UserService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: UserDtoRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("UserService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: SaveUserDto ⇒
        val id = state.users.size
        state = UserDtoRepository(state.users + (id -> c.userDto))
      case c: DeleteUserByUserId =>
        state = UserDtoRepository(state.users - c.userId)
      case c: UpdateUser ⇒
        val id = state.users.size
        state = UserDtoRepository(state.users + (id -> c.user))
    }


    override def receiveCommand: Receive = {
      case c:GetTransferSearch =>
        val queryInfoFirstSection: TripInfo= TripInfo(c.trasnferTravelInfo.fromStationName,
            c.trasnferTravelInfo.viaStationName,
            c.trasnferTravelInfo.travelDate)
        val firstSectionFromHighSpeed: List[TripResponse] = tripsFromHighSpeed(queryInfoFirstSection)
        val firstSectionFromNormal: List[TripResponse] =  tripsFromNormal(queryInfoFirstSection)
        val queryInfoSecondSection: TripInfo= TripInfo(c.trasnferTravelInfo.viaStationName,
            c.trasnferTravelInfo.toStationName,
            c.trasnferTravelInfo.travelDate)
        val secondSectionFromHighSpeed: List[TripResponse] = tripsFromHighSpeed(queryInfoSecondSection)
        val secondSectionFromNormal: List[TripResponse] =  tripsFromNormal(queryInfoSecondSection)
        val firstSection:List[TripResponse] = firstSectionFromHighSpeed:::firstSectionFromNormal
        val secondSection: List[TripResponse] = secondSectionFromHighSpeed ::: secondSectionFromNormal
        sender() ! Response(0,"Success", TransferTravelResult(firstSection,secondSection))
      case c:GetCheapest =>
        val routePlanInfo = RoutePlanInfo(c.tripInfo.startingPlace,
          c.tripInfo.endPlace,c.tripInfo.departureTime,5)
        val routePlanResultUnits: List[RoutePlanResultUnit] = getRoutePlanResultCheapest(routePlanInfo)
        if (routePlanResultUnits.nonEmpty) {
          var lists: List[TravelAdvanceResultUnit] = List()
          var i = 0
          while ( {i < routePlanResultUnits.size}) {
            val tempUnit = routePlanResultUnits(i)
            import TSCommon.Commons.SeatClass
            val first: Int = getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().firstClass._1)
            val second: Int= getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().secondClass._1)
            val newUnit = TravelAdvanceResultUnit(tempUnit.tripId,
              tempUnit.trainTypeId,
              tempUnit.fromStationName,
              tempUnit.toStationName,
              transferStationIdToStationName(tempUnit.stopStations).get,
              tempUnit.priceForSecondClassSeat,
              second,
              tempUnit.priceForFirstClassSeat,
              first)
            lists = newUnit :: lists
              i += 1; i - 1
          }
          sender()!  Response(0, "Success", lists.reverse)
        }
        else sender()!  Response(1, "Success", None)
      case c:GetQuickest =>
        val routePlanInfo: RoutePlanInfo = RoutePlanInfo(c.tripInfo.startingPlace,c.tripInfo.endPlace,c.tripInfo.departureTime,5)
        val routePlanResultUnits:List[RoutePlanResultUnit] = getRoutePlanResultQuickest(routePlanInfo)
        if (routePlanResultUnits.nonEmpty) { // ArrayList<RoutePlanResultUnit> routePlanResultUnits =   routePlanResults.getData();
          var lists: List[TravelAdvanceResultUnit] = List()
          var i = 0
          while ( {i < routePlanResultUnits.size}) {
            val tempUnit = routePlanResultUnits(i)
            val first: Int = getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().firstClass._1)
            val second: Int= getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().secondClass._1)
            val newUnit = TravelAdvanceResultUnit(tempUnit.tripId,
              tempUnit.trainTypeId,
              tempUnit.fromStationName,
              tempUnit.toStationName,
              transferStationIdToStationName(tempUnit.stopStations).get,
              tempUnit.priceForSecondClassSeat,
              second,
              tempUnit.priceForFirstClassSeat,
              first)
            lists = newUnit ::lists

            {
              i += 1; i - 1
            }
          }
          sender()!  Response(0, "Success", lists.reverse)
        }
        else sender()!  Response(1, "Success", None)


      case c:GetMinStation =>
        val routePlanInfo: RoutePlanInfo = RoutePlanInfo(c.tripInfo.startingPlace,c.tripInfo.endPlace,c.tripInfo.departureTime,5)
        val routePlanResultUnits:List[RoutePlanResultUnit] = getRoutePlanResultMinStation(routePlanInfo)
        if (routePlanResultUnits.nonEmpty) { // ArrayList<RoutePlanResultUnit> routePlanResultUnits =   routePlanResults.getData();
          var lists: List[TravelAdvanceResultUnit] = List()
          var i = 0
          while ( {i < routePlanResultUnits.size}) {
            val tempUnit = routePlanResultUnits(i)
            val first: Int = getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().firstClass._1)
            val second: Int= getRestTicketNumber(c.tripInfo.departureTime, tempUnit.tripId, tempUnit.fromStationName, tempUnit.toStationName, SeatClass().secondClass._1)
            val newUnit = TravelAdvanceResultUnit(tempUnit.tripId,
              tempUnit.trainTypeId,
              tempUnit.fromStationName,
              tempUnit.toStationName,
              transferStationIdToStationName(tempUnit.stopStations).get,
              tempUnit.priceForSecondClassSeat,
              second,
              tempUnit.priceForFirstClassSeat,
              first)
            lists = newUnit ::lists

            {
              i += 1; i - 1
            }
          }
          sender()!  Response(0, "Success", lists.reverse)
        }
        else sender()!  Response(1, "Success", None)

      case GetRestTicketNumber =>
    }


    private def getRestTicketNumber(travelDate: Date, trainNumber: Int, startStationName: String, endStationName: String, seatType: Int): Int = {
      var leftSeats: Option[Int] = None
      val seatRequest = Seat(travelDate,trainNumber,queryForStationId(startStationName).get, queryForStationId(endStationName).get,seatType)
      val response: Future[Any] = seatService ? GetLeftTicketOfInterval(seatRequest)
      response onComplete{
        case Success(resp) =>
          if (resp.asInstanceOf[Response].status == 0) leftSeats = Some(resp.asInstanceOf[Response].data.asInstanceOf[Int])
        case Failure(_) =>
          leftSeats = None
      }
      leftSeats.get
    }

    private def getRoutePlanResultCheapest(info: RoutePlanInfo):List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val response: Future[Any] = routePlanService ?  SearchCheapestResult(info)
      response onComplete{
        case Success(resp) =>
          if (resp.asInstanceOf[Response].status == 0) routePlanResultUnits = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[RoutePlanResultUnit]])
        case Failure(_) =>
          routePlanResultUnits = None
      }
      routePlanResultUnits.get
    }

    private def getRoutePlanResultQuickest(info:RoutePlanInfo ):List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val response: Future[Any] = routePlanService ?  SearchQuickestResult(info)
      response onComplete{
        case Success(resp) =>
          if (resp.asInstanceOf[Response].status == 0) routePlanResultUnits = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[RoutePlanResultUnit]])
        case Failure(_) =>
          routePlanResultUnits = None
      }
      routePlanResultUnits.get
    }

    private def getRoutePlanResultMinStation(info: RoutePlanInfo): List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val response: Future[Any] = routePlanService ?  SearchMinStopStations(info)
      response onComplete{
        case Success(resp) =>
          if (resp.asInstanceOf[Response].status == 0) routePlanResultUnits = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[RoutePlanResultUnit]])
        case Failure(_) =>
          routePlanResultUnits = None
      }
      routePlanResultUnits.get
    }

    def tripsFromHighSpeed(info: TripInfo): List[TripResponse]  = {
      var result: Option[List[TripResponse]] = None
      val response = travelService ? QueryTravel(info)
      response onComplete {
        case Success(resp) =>
          if(resp.asInstanceOf[Response].status == 0) result = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[TripResponse]])
        case Failure(_) =>
          result = None
      }
      result.get

    }

    def tripsFromNormal(info: TripInfo): List[TripResponse] = {
      var result: Option[List[TripResponse]] = None
      val response: Future[Any] = travel2service ? QueryTravel(info)
      response onComplete {
        case Success(resp) =>
          if(resp.asInstanceOf[Response].status == 0) result = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[TripResponse]])
        case Failure(_) =>
          result = None
      }
      result.get
    }

    def queryForStationId(stationName: String):Option[Int] = {
      var result: Option[Int] = None
      val response: Future[Any] = ticketInfoService ? QueryForStationId(stationName)
      response onComplete {
        case Success(resp) =>
          if(resp.asInstanceOf[Response].status == 0) result = Some(resp.asInstanceOf[Response].data.asInstanceOf[Int])
        case Failure(_) =>
          result = None
      }
      result
    }

    def transferStationIdToStationName(stations: List[Int]) :Option[List[String]] = {
      var result: Option[List[String]] = None
      val response: Future[Any] = stationService ? QueryByIdBatchStation(stations)
      response onComplete {
        case Success(resp) =>
          if(resp.asInstanceOf[Response].status == 0) result = Some(resp.asInstanceOf[Response].data.asInstanceOf[List[String]])
        case Failure(_) =>
          result = None
      }
      result
    }
  }

}
