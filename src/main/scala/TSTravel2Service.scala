import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout
import InputData._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.{Failure, Success}
import java.util.{Calendar, Date}


object TSTravel2Service{
  case class TripRepository(trips: Map[Int, Trip])
  class Travel2Service(routeService: ActorRef , trainService: ActorRef , trainTicketInfoService: ActorRef ,
                      orderOtherService: ActorRef,SeatService: ActorRef ) extends PersistentActor {
    var state: TripRepository = TripRepository(trips.zipWithIndex.map(a => a._2+1 ->a._1).toMap)
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
        if (state.trips.contains(c.travelInfo.tripId)) {
          sender() ! Response(1, "Trip allready exist", None)
        }
        else {
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
              endPlaceId, startingPlaceName, endPlaceName, c.tripInfo.departureTime).get
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
            response  match {
              case Some(result) =>
                gtdr = TripAllDetail(result, trip)
                println("GetTripAllDetailInfo success")
                sender() ! Response(0, "Success", gtdr)
              case None =>
                sender() ! Response(1, "no tickets found", gtdr)

            }


          case None =>
            sender() ! Response(1, "no trip found", gtdr)
        }


      case GetRouteByTripId(tripId: Int) =>
        state.trips.get(tripId) match {
          case Some(trip) =>
            getRouteByRouteId(trip.routeId) match {
              case Some(route) => sender() ! Response(0, "Success", route)
              case None => sender() ! Response(1, "Failed", None)
            }
          case None => sender() ! Response(1, "No trip found", None)

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
          case None =>
            sender() ! Response(1, "No trip found", None)
        }
      case QueryAllTravel =>
        sender() ! Response(0, "success", state.trips.values.toList)
      case c: GetTripByRoute =>

        var tripList: List[Trip] = List()
        for (routeId <- c.routeIds) {
          val tempTripList = getTripsByRouteId(routeId)
          tripList = tripList ++ tempTripList
        }
        if (tripList.nonEmpty) sender() ! Response(0, "Success", tripList)
        else sender() ! Response(1, "No Content", None)
      case AdminQueryAll =>
        var adminTrips: List[AdminTrip] = List()
        for (trip <- state.trips.values) {

          val adminTrip = AdminTrip(trip, getTrainType(trip.trainTypeId).get, getRouteByRouteId(trip.routeId).get)

          adminTrips = adminTrip :: adminTrips
        }
        if (adminTrips.nonEmpty) sender() ! Response(0, "Success", adminTrips.reverse)
        else sender() ! Response(1, "No Content", None)

    }

    def queryForStationId(stationName: String): Option[Int] = {
      var id: Option[Int] = None
      val responseFuture: Future[Any] = trainTicketInfoService ? QueryForStationId(stationName)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) id = Some(response.data.asInstanceOf[Int])
      id
    }

    def getTickets(trip: Trip, route: Route, startingPlaceId: Int, endPlaceId: Int, startingPlaceName: String,
                   endPlaceName: String, departureTime: Date): Option[TripResponse] = {
      if (!afterToday(departureTime)) None
      println("TRIP: "+trip)
      println("Route: "+ route)
      println("from: "+ startingPlaceName+ " "+ startingPlaceId)
      println("to: "+ endPlaceName+ " "+ endPlaceId)

      val travel = Travel(trip, startingPlaceName, endPlaceName, departureTime)
      val response = TripResponse()
      val travelResultResponseFuture: Future[Any] = trainTicketInfoService ? QueryForTravel(travel)
      val travelResultResponse = Await.result(travelResultResponseFuture,duration).asInstanceOf[Response]
      if (travelResultResponse.status == 0) {
        val travelResult = travelResultResponse.data.asInstanceOf[TravelResult]
        var soldTicket: SoldTicket = null
        val soldTicketResponseFuture: Future[Any] = orderOtherService ? QueryAlreadySoldOrders(travelDate = departureTime, trainNumber = trip.tripId)
        val soldTicketResponse = Await.result(soldTicketResponseFuture,duration).asInstanceOf[Response]
        if (soldTicketResponse.status == 0)
          soldTicket = soldTicketResponse.data.asInstanceOf[SoldTicket]
        if (queryForStationId(startingPlaceName).get.equals(trip.startingStationId) && queryForStationId(endPlaceName).get.equals(trip.terminalStationId)) {
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
        response.confortClass = first.get
        response.economyClass = second.get
        response.startingStation = startingPlaceName
        response.terminalStation = endPlaceName
        val indexStart: Int = route.stations.indexOf(startingPlaceId)
        val indexEnd: Int = route.stations.indexOf(endPlaceId)
        println("Travel2service:getTickets->Route: "+ route)
        println("Travel2service:getTickets->Route: "+ route)
        println("Travel2service:getTickets->indexEnd: "+ indexEnd)
        println("Travel2service:getTickets->indexStart: "+ startingPlaceId)

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
        response.priceForConfortClass = travelResult.prices.get("confortClass").get
        response.priceForEconomyClass = travelResult.prices.get("economyClass").get
        println("getTickets Success")
        Some(response)
      } else None
    }

    def afterToday(date: Date): Boolean = {
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
                            seatType: Int): Option[Int] = {
      val seatRequest = Seat()
      var restoftheTickets: Option[Int] = None
      val fromId = queryForStationId(startStationName)
      val toId = queryForStationId(endStationName)
      seatRequest.destStation = toId.get
      seatRequest.startStation = fromId.get
      seatRequest.trainNumber = trainNumber
      seatRequest.travelDate = travelDate
      seatRequest.seatType = seatType
      System.out.println("Seat request To String: " + seatRequest.toString)
      val responseFuture: Future[Any] = SeatService ? GetLeftTicketOfInterval(seatRequest)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) restoftheTickets = Some(response.data.asInstanceOf[Int])
      restoftheTickets
    }

    def getTrainType(traintypeId: Int): Option[TrainType] = {
      var trainType: Option[TrainType] = None
      val responseFuture: Future[Any] = trainService ? RetrieveTrain(traintypeId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) trainType = Some(response.data.asInstanceOf[TrainType])
      trainType
    }

    def getRouteByRouteId(routeId: Int): Option[Route] = {
      var route: Option[Route] = None
      val responseFuture: Future[Any] = routeService ? GetRouteById(routeId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) {
        route = Some(response.data.asInstanceOf[Route])
      }
      route
    }

    def getTripsByRouteId(routeId: Int): List[Trip] = {
      state.trips.values.filter(trip => trip.routeId == routeId).toList
    }

  }

}
