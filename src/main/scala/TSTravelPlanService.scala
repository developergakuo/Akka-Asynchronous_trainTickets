import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.{Failure, Success}
import java.util.Date

object TSTravelPlanService {

  class TravelPlanService(authService: ActorRef , ticketInfoService: ActorRef , stationService: ActorRef ,
                          travel2service: ActorRef , travelService: ActorRef , seatService: ActorRef ,
                          routePlanService: ActorRef) extends Actor {

    override def receive: Receive = {
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
        println("======== TravelPlan:GetCheapest " )
        val routePlanInfo =
          RoutePlanInfo(c.tripInfo.startingPlace,
          c.tripInfo.endPlace,c.tripInfo.departureTime,5)
        val routePlanResultUnits: List[RoutePlanResultUnit] = getRoutePlanResultCheapest(routePlanInfo)
        if (routePlanResultUnits.nonEmpty) {
          println("routePlanResultUnits: "+routePlanResultUnits)
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
          println("TravelPlanServiceGetCheapest: Success"+lists.reverse)
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
      val responseFuture: Future[Any] = seatService ? GetLeftTicketOfInterval(seatRequest)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if (response.asInstanceOf[Response].status == 0) leftSeats = Some(response.data.asInstanceOf[Int])
      println("LeftsSeats: "+ leftSeats.get)
      leftSeats.get
    }

    private def getRoutePlanResultCheapest(info: RoutePlanInfo):List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val responseFuture: Future[Any] = routePlanService ?  SearchCheapestResult(info)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if (response.status == 0) routePlanResultUnits = Some(response.data.asInstanceOf[List[RoutePlanResultUnit]])
      routePlanResultUnits.get
    }

    private def getRoutePlanResultQuickest(info:RoutePlanInfo ):List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val responseFuture: Future[Any] = routePlanService ?  SearchQuickestResult(info)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if (response.status == 0) routePlanResultUnits = Some(response.data.asInstanceOf[List[RoutePlanResultUnit]])
      routePlanResultUnits.get
    }

    private def getRoutePlanResultMinStation(info: RoutePlanInfo): List[RoutePlanResultUnit] = {
      var routePlanResultUnits: Option[List[RoutePlanResultUnit]] = None
      val responseFuture: Future[Any] = routePlanService ?  SearchMinStopStations(info)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if (response.status == 0) routePlanResultUnits = Some(response.data.asInstanceOf[List[RoutePlanResultUnit]])
      routePlanResultUnits.get
    }

    def tripsFromHighSpeed(info: TripInfo): List[TripResponse]  = {
      var result: Option[List[TripResponse]] = None
      val responseFuture = travelService ? QueryTravel(info)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if(response.status == 0) result = Some(response.data.asInstanceOf[List[TripResponse]])
      result.get
    }

    def tripsFromNormal(info: TripInfo): List[TripResponse] = {
      var result: Option[List[TripResponse]] = None
      val responseFuture: Future[Any] = travel2service ? QueryTravel(info)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if(response.status == 0) result = Some(response.data.asInstanceOf[List[TripResponse]])
      result.get
    }

    def queryForStationId(stationName: String):Option[Int] = {
      var result: Option[Int] = None
      val responseFuture: Future[Any] = ticketInfoService ? QueryForStationId(stationName)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
      if(response.status == 0) result = Some(response.data.asInstanceOf[Int])
      result
    }

    def transferStationIdToStationName(stations: List[Int]) :Option[List[String]] = {
      var result: Option[List[String]] = None
      val responseFuture: Future[Any] = stationService ? QueryByIdBatchStation(stations)
      val response = Await.result(responseFuture, duration).asInstanceOf[Response]
          if(response.status == 0) result = Some(response.data.asInstanceOf[List[String]])
      result
    }
  }
}
