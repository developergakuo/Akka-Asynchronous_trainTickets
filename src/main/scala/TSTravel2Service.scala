import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import java.util.{Calendar, Date}

implicit val timeout: Timeout = 2.seconds

object TSTravel2Service {
  case class TripRepository(trips: Map[Int, Trip])

  class Travel2Service extends PersistentActor {
    var state: TripRepository = TripRepository(Map())
    var routeService: ActorRef = null
    var trainService: ActorRef = null
    var trainTicketInfoService: ActorRef = null
    var orderService: ActorRef = null
    var seatService: ActorRef = null

    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "TravelService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: TripRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateTravel ⇒
        val trip =
          Trip(c.travelInfo.tripId, c.travelInfo.trainTypeId,
            c.travelInfo.routeId, c.travelInfo.startingTime,
            c.travelInfo.startingStationId, c.travelInfo.stationsId,
            c.travelInfo.terminalStationId)
        state = TripRepository(state.trips + (c.travelInfo.tripId -> trip))

      case c: UpdateTravel =>
        state =
          TripRepository(state.trips + (c.travelInfo.tripId ->
            Trip(c.travelInfo.tripId, c.travelInfo.trainTypeId,
              c.travelInfo.routeId, c.travelInfo.startingTime,
              c.travelInfo.startingStationId, c.travelInfo.stationsId,
              c.travelInfo.terminalStationId)))
      case c: DeleteTravel =>
        state = TripRepository(state.trips - c.tripId)

    }


    override def receiveCommand: Receive = {

      case c: CreateTravel =>
       state.trips.get(c.travelInfo.tripId) match{
         case Some(_) =>
          sender() ! Response(1, "Trip already exist", None)
         case None =>
           persistAsync(c)(updateState)
           sender() ! Response(0, "Success", None)
        }

      case RetrieveTravel(tripId: Int) =>
        val trip: Option[Trip] = state.trips.get(tripId)
        trip match {
          case Some(trp) =>
            sender() ! Response(0, "Success", trp)
          case None =>
            sender() ! Response(1, "No trip by that id", None)
        }
      case c: UpdateTravel =>
        state.trips.get(c.travelInfo.tripId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "No trip found", None)
        }

      case c: DeleteTravel =>
        state.trips.get(c.tripId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "No trip found", None)
        }
      case c: QueryTravel =>
        val startingPlaceName = c.tripInfo.startingPlace
        val endPlaceName = c.tripInfo.endPlace
        val startingPlaceId = queryForStationId(startingPlaceName).get
        val endPlaceId = queryForStationId(endPlaceName).get
        var tripResponses: List[TripResponse] = List()
        for (tripEntry <- state.trips) {
          val tempRoute = getRouteByRouteId(tripEntry._2.routeId).get
          if (tempRoute.stations.contains(startingPlaceId) &&
            tempRoute.stations.contains(endPlaceId) &&
            tempRoute.stations.indexOf(startingPlaceId) < tempRoute.stations.indexOf(endPlaceId)) {
            val response: TripResponse = getTickets(tripEntry._2, tempRoute, startingPlaceId,
              endPlaceId, startingPlaceName, endPlaceName, c.tripInfo.departureTime)
            tripResponses = response :: tripResponses
          }
        }
        sender() ! Response(0, "Success", tripResponses)

      case c: GetTripAllDetailInfo =>
        import TSCommon.Commons.TripAllDetail
        var gtdr: TripAllDetail = null
        state.trips.get(c.gtdi.tripId) match {
          case Some(trip) =>
            val startingPlaceName = c.gtdi.from
            val endPlaceName = c.gtdi.to
            val startingPlaceId = queryForStationId(startingPlaceName)
            val endPlaceId = queryForStationId(endPlaceName)
            val tempRoute = getRouteByRouteId(trip.routeId).get
            val response = getTickets(trip, tempRoute, startingPlaceId.get,
              endPlaceId.get, startingPlaceName, endPlaceName, c.gtdi.travelDate)
            if (response != null) {
              gtdr = TripAllDetail(response, trip)
            }
            sender() ! Response(0, "Success", gtdr)
          case None =>
            sender() ! Response(1, "no trip found", gtdr)
        }

      case GetRouteByTripId(tripId: Int) =>
        state.trips.get(tripId) match {
          case Some(trip) =>
            getRouteByRouteId(trip.routeId) match {
              case Some(route) => sender() ! Response(0,"Success",route)
              case None => sender() ! Response(1,"Failed",None)
            }
          case None => sender() ! Response(0,"No trip found",None)
        }

      case GetTrainTypeByTripId(tripId: Int) =>
        state.trips.get(tripId) match {
          case Some(trip) =>
            getTrainType(trip.trainTypeId) match {
              case Some(trainType) =>
                sender() ! Response(0, "Success", trainType)
              case None =>
                sender() ! Response(1, "Failed", None)
            }
          case  None =>
            sender() ! Response(1, "No trip found", None)
        }
      case QueryAllTravel =>
        sender() ! Response(0, "success", state.trips.values.toList)
      case c: GetTripByRoute =>

        var tripList: List[Trip] = List()
        for (routeId <- c.routeIds) {
          val tempTripList =getTripsByRouteId(routeId)
          tripList = tripList ++ tempTripList
        }
        if (tripList.nonEmpty ) sender() ! Response(0, "Success", tripList)
        else sender() ! Response(1, "No Content", None)
      case AdminQueryAll =>
        var adminTrips: List[AdminTrip] = List()
        for ( trip <- state.trips.values) {

          val adminTrip = AdminTrip(trip,getTrainType(trip.trainTypeId).get,getRouteByRouteId(trip.routeId).get)

          adminTrips = adminTrip :: adminTrips
        }
        if (adminTrips.nonEmpty ) sender() !  Response(0, "Success", adminTrips.reverse)
        else sender() !  Response(1, "No Content", None)
    }

    def queryForStationId(stationName: String): Option[Int] = {
      var id: Option[Int] = None
      val response: Future[Any] = trainTicketInfoService ? QueryForStationId(stationName)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) id = Some(res.asInstanceOf[Response].data.asInstanceOf[Int])
          else id = None
        case Failure(_) =>
          id = None
      }
      id
    }

    def getTickets(trip: Trip, route: Route, startingPlaceId: Int, endPlaceId: Int, startingPlaceName: String,
                   endPlaceName: String, departureTime: Date): TripResponse = {
      if (!afterToday(departureTime)) return null
      val travel = Travel(trip,startingPlaceName,endPlaceName,departureTime)
      val response = TripResponse()
      val travelResultResponse: Future[Any] = trainTicketInfoService ? QueryForTravel(travel)
      travelResultResponse onComplete{
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0)
          {
            val travelResult = res.asInstanceOf[Response].data.asInstanceOf[TravelResult]
            var soldTicket: SoldTicket = null
            val soldTicketResponse : Future[Any] = orderService ? GetSoldTickets(Seat(travelDate = departureTime,trainNumber = trip.tripId))
            soldTicketResponse onComplete{
              case Success(resp2) =>
                if (resp2.asInstanceOf[Response].status ==0){
                  soldTicket= resp2.asInstanceOf[Response].data.asInstanceOf[SoldTicket]
                }
            }

            if (queryForStationId(startingPlaceName).get.equals(trip.stationsId) && queryForStationId(endPlaceName).get.equals(trip.terminalStationId)) {
              response.confortClass = 50
              response.economyClass = 50
            }
            else {
              response.confortClass = 50
              response.economyClass = 50
            }
            val first = getRestTicketNumber(departureTime, trip.tripId,
              startingPlaceName, endPlaceName, SeatClass().firstClass._1)
            val second = getRestTicketNumber(departureTime, trip.tripId,
              startingPlaceName, endPlaceName, SeatClass().secondClass._1)
            response.confortClass = first
            response.economyClass = second
            response.startingStation = startingPlaceName
            response.terminalStation = endPlaceName
            import TSCommon.Commons.TrainType
            import java.util.Calendar
            val indexStart: Int = route.stations.indexOf(startingPlaceId)
            val indexEnd: Int = route.stations.indexOf(endPlaceId)
            val distanceStart: Int = route.distances(indexStart) - route.distances.head
            val distanceEnd: Int = route.distances(indexEnd) - route.distances.head
            val trainType: TrainType = getTrainType(trip.trainTypeId).get
            val minutesStart: Int = 60 * distanceStart / trainType.averageSpeed
            val minutesEnd: Int = 60 * distanceEnd / trainType.averageSpeed
            val calendarStart: Calendar = Calendar.getInstance
            calendarStart.setTime(trip.startingTime)
            calendarStart.add(Calendar.MINUTE, minutesStart)
            response.startingTime = calendarStart.getTime
            val calendarEnd: Calendar = Calendar.getInstance
            calendarEnd.setTime(trip.startingTime)
            calendarEnd.add(Calendar.MINUTE, minutesEnd)
            response.endTime = calendarEnd.getTime
            response.tripId = soldTicket.trainNumber
            response.trainTypeId = trip.trainTypeId
            response.priceForConfortClass =travelResult.prices.get("confortClass").get
            response.priceForEconomyClass = travelResult.prices.get("economyClass").get
          }
      }
      response
    }


    def afterToday(date: Date) = {
      val calDateA = Calendar.getInstance
      val today = new Date()
      calDateA.setTime(today)
      val calDateB = Calendar.getInstance
      calDateB.setTime(date)
      if (calDateA.get(Calendar.YEAR) > calDateB.get(Calendar.YEAR)) false
      else if (calDateA.get(Calendar.YEAR) == calDateB.get(Calendar.YEAR)) if (calDateA.get(Calendar.MONTH) > calDateB.get(Calendar.MONTH)) false
      else if (calDateA.get(Calendar.MONTH) == calDateB.get(Calendar.MONTH)) if (calDateA.get(Calendar.DAY_OF_MONTH) > calDateB.get(Calendar.DAY_OF_MONTH)) false
      else true
      else true
      else true
    }


    def getRestTicketNumber(travelDate: Date, trainNumber: Int, startStationName: String, endStationName: String,
                            seatType: Int):Int = {
      val seatRequest = Seat()
      var restoftheTickets: Int = 0
      val fromId = queryForStationId(startStationName)
      val toId = queryForStationId(endStationName)
      seatRequest.destStation= toId.get
      seatRequest. startStation =fromId.get
      seatRequest.trainNumber = trainNumber
      seatRequest.travelDate = travelDate
      seatRequest.seatType = seatType

      System.out.println("Seat request To String: " + seatRequest.toString)
      val response: Future[Any] = seatService ? GetLeftTicketOfInterval(seatRequest)
      response onComplete{
        case Success(res)=>
          if(res.asInstanceOf[Response].status == 0) restoftheTickets = res.asInstanceOf[Response].data.asInstanceOf[Int]
      }
      restoftheTickets
    }

    def getTrainType(traintypeId: Int):  Option[TrainType] ={
      var trainType: Option[TrainType] = None
      val response: Future[Any] = trainService ? GetTrainType(traintypeId)
      response onComplete {
        case Success(resp: Response) =>
          if (resp.status == 0) trainType = Some(resp.data.asInstanceOf[TrainType])
        case Failure(_) =>
          trainType = None
      }
      trainType
    }

    def getRouteByRouteId(routeId: Int): Option[Route] = {
      var route: Option[Route] = None
      val response: Future[Any] = routeService ? GetRouteById(routeId)
      response onComplete {
        case Success(value) =>
          val resp: Response = value.asInstanceOf[Response]
          if (resp.status == 0) {
            route = Some(resp.data.asInstanceOf[Route])
          } else {
            route = None
          }
        case Failure(_) =>
          route = None
      }
      route
    }
    def getTripsByRouteId(routeId: Int): List[Trip] ={
      state.trips.values.filter(trip=>trip.routeId == routeId).toList
    }
  }

}


