import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence._
import akka.pattern.ask
import InputData._

import scala.concurrent.Future
import scala.concurrent.Await
import java.util.{Calendar, Date}

import scala.collection.mutable.ListBuffer



object TSTravel2Service{

  case class QueryTravelState(requests: Map[(String,ActorRef,Int),(List[TripResponses],List[TripResponse]  )])

  case class TripResponses(var startingPlaceName: String = "", var endPlaceName: String = "", var startingPlaceId: Int = -1,
                           var endPlaceId: Int = -1, var travelResult: Option[TravelResult] = None,
                           var trip: Option[Trip] = None, depatureTime: Option[Date] = Some(new Date()),
                           var soldTicket: Option[SoldTicket]=None, var temRoute: Option[Route] = None)
  case class GetTripAllDetailInfoState(requests: Map[(String,ActorRef,Int),(TripResponses,List[TripResponse]  )])

  case class TravelServiceState(tripRepository: TripRepository, queryTravelState: QueryTravelState, getTripAllDetailInfoState: GetTripAllDetailInfoState)

  case class TripRepository(trips: Map[Int, Trip])
  class Travel2Service(routeService: ActorRef , trainService: ActorRef , trainTicketInfoService: ActorRef ,
                      orderOtherService: ActorRef,SeatService: ActorRef ) extends PersistentActor  with  AtLeastOnceDelivery{

    var state: TravelServiceState = TravelServiceState(TripRepository(trips.zipWithIndex.map(a => a._2+1 ->a._1).toMap),QueryTravelState(Map()),GetTripAllDetailInfoState(Map()) )
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
      case SnapshotOffer(_, offeredSnapshot: TravelServiceState) ⇒ state = offeredSnapshot
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
        state = TravelServiceState(TripRepository(state.tripRepository.trips + (c.travelInfo.tripId -> trip) ),
          state.queryTravelState, state.getTripAllDetailInfoState)

      case c: UpdateTravel =>
        state =
          TravelServiceState(TripRepository(state.tripRepository.trips + (c.travelInfo.tripId ->
            Trip(c.travelInfo.tripId, c.travelInfo.trainTypeId,
              c.travelInfo.routeId, c.travelInfo.startingTime,
              c.travelInfo.startingStationId, c.travelInfo.stationsId,
              c.travelInfo.terminalStationId))),state.queryTravelState,state.getTripAllDetailInfoState)
      case c: DeleteTravel =>
        state = TravelServiceState(TripRepository(state.tripRepository.trips - c.tripId), state.queryTravelState, state.getTripAllDetailInfoState)
      case c: GetTripAllDetailInfo =>
        var (_,responses )= state.getTripAllDetailInfoState.requests.getOrElse(("GetTripAllDetailInfo", c.requester, c.requestId), (TripResponses(),List()))
        responses = TripResponse():: responses
        val qState =
          TripResponses(startingPlaceName = c.gtdi.from, endPlaceName = c.gtdi.to,
            startingPlaceId = queryForStationId(c.gtdi.from).get,endPlaceId = queryForStationId(c.gtdi.to).get,
            trip = Some(c.tempTrip.get), temRoute = c.temRoute,depatureTime = Some(c.gtdi.travelDate))
        state = TravelServiceState(state.tripRepository, state.queryTravelState,
          GetTripAllDetailInfoState(state.getTripAllDetailInfoState.requests + (("GetTripAllDetailInfo", c.requester, c.requestId) -> (qState,responses))))

      case c: QueryTravel =>
        println("c-trip: "+ c.trip_route)
        println("persisted: "+ c)
        println("1: " + c.requester +"  "+ c.RequestId)
        var (_,responses )= state.queryTravelState.requests.getOrElse(("QueryTravel", sender(), c.RequestId), (List(),List()))
        println("QueryTravel here: "+" "+ responses)
        println("QueryTravel here: "+" "+ responses)
        val trip_route = c.trip_route
        val qstates = trip_route.map(a =>
          TripResponses(startingPlaceName = c.tripInfo.startingPlace,
            startingPlaceId = queryForStationId(c.tripInfo.startingPlace).get,
            endPlaceName = c.tripInfo.endPlace, trip =Some(a._1) ,
            endPlaceId = queryForStationId(c.tripInfo.endPlace).get,
            temRoute = Some(a._2), depatureTime =Some(c.tripInfo.departureTime)))

        state = TravelServiceState(state.tripRepository,QueryTravelState(state.queryTravelState.requests + (("QueryTravel", sender(), c.RequestId) -> (qstates,responses))),
          state.getTripAllDetailInfoState)
        println("requests1: "+state.queryTravelState.requests)
      case c: ResponseQueryForTravel =>
        confirmDelivery(c.deliveryID)
        if (c.found) {
          if (c.requestLabel.equals("QueryTravel")) {
            println("ResponseQueryForTravel: "+c.travel)
            println("2: " +c.requester +"  "+ c.requestId + " "+ c.tripId)
            println("qstates: "+state.queryTravelState.requests.get(("QueryTravel", c.requester, c.requestId)).get._1)
            val qstate = state.queryTravelState.requests.get(("QueryTravel", c.requester, c.requestId)).get._1.filter(q=>q.trip.get.tripId == c.tripId).head
            println("Custome qstate: "+ qstate.trip.get.tripId)
            qstate.travelResult = Some(c.travel)
            deliver(orderOtherService.path)(deliveryId => QueryAlreadySoldOrders(qstate.depatureTime.get, qstate.trip.get.tripId, deliveryId, c.requester, c.requestId, c.requestLabel, c.label,c.sender))

          }
          else if(c.requestLabel.equals("GetTripAllDetailInfo")){
            val qstate = state.getTripAllDetailInfoState.requests.get((c.requestLabel, c.requester, c.requestId)).get._1
            qstate.travelResult = Some(c.travel)
            deliver(orderOtherService.path)(deliveryId =>
              QueryAlreadySoldOrders(qstate.depatureTime.get, qstate.trip.get.tripId, deliveryId, c.requester, c.requestId, c.requestLabel, c.label,c.sender))

          }
        }
      case c: ResponseQueryAlreadySoldOrders =>
        confirmDelivery(c.deliveryId)
        if (c.found) {
          var pre_qstate: Option[TripResponses] = None
          var pre_response: Option[TripResponse] = None
          if (c.requestLabel.equals("QueryTravel")){
            println("Missing: "+c.tripId)
            pre_qstate = Some(state.queryTravelState.requests.get((c.requestLabel, c.requester, c.requestId)).get._1.filter(q=>q.trip.get.tripId == c.tripId).head)
            pre_response = Some(TripResponse())
            println("Thelabel soldTicket: " + c.soldTicket)
          }
          else if(c.requestLabel.equals("GetTripAllDetailInfo")){
            pre_qstate = Some(state.getTripAllDetailInfoState.requests.get((c.requestLabel, c.requester, c.requestId)).get._1)
            pre_response = Some(state.getTripAllDetailInfoState.requests.get((c.requestLabel, c.requester, c.requestId)).get._2.head)

          }
          val response = pre_response.get
          val qstate = pre_qstate.get
          println("qstate trip: "+ qstate.trip)
          val soldTicket = c.soldTicket
          qstate.soldTicket = Some(soldTicket)
          val startingPlaceId = qstate.startingPlaceId
          val startingPlaceName = qstate.startingPlaceName
          val endPlaceId = qstate.endPlaceId
          val endPlaceName = qstate.endPlaceName
          val trip = qstate.trip.get
          val departureTime = qstate.depatureTime
          val route = qstate.temRoute.get
          println("Route:  "+ route)
          val travelResult = qstate.travelResult.get


          if (startingPlaceId.equals(trip.startingStationId) && endPlaceId.equals(trip.terminalStationId)) {
            response.confortClass = 50
            response.economyClass = 50
          }
          else {
            response.confortClass = 50
            response.economyClass = 50
          }
          val first = getRestTicketNumber(departureTime.get, trip.tripId,
            startingPlaceName, endPlaceName, SeatClass().firstClass._1)
          val second = getRestTicketNumber(departureTime.get, trip.tripId,
            startingPlaceName, endPlaceName, SeatClass().secondClass._1)
          response.confortClass = first.get
          response.economyClass = second.get
          response.startingStation = startingPlaceName
          response.terminalStation = endPlaceName
          val indexStart: Int = route.stations.indexOf(startingPlaceId)
          val indexEnd: Int = route.stations.indexOf(endPlaceId)
          println("Travel2service:getTickets->Route: " + route)
          println("Travel2service:getTickets->start and end: " + startingPlaceId + " "+ endPlaceId)
          println("Travel2service:getTickets->indexEnd: " + indexEnd)
          println("Travel2service:getTickets->indexStart: " + indexStart)

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
          if(c.requestLabel.equals("QueryTravel")){
            println("Thelabel response: " + response)


          }
          if (c.requestLabel.equals("QueryTravel")){
            var (qstates,responses )= state.queryTravelState.requests.getOrElse(("QueryTravel", c.requester, c.requestId), (List(),List()))
            responses = response :: responses
            println("custome responses:"+ responses )
            state = TravelServiceState(state.tripRepository,QueryTravelState(state.queryTravelState.requests + (("QueryTravel", c.requester, c.requestId) -> (qstates,responses))),
              state.getTripAllDetailInfoState)
            if(responses.size == qstates.size) {
              println("QueryTravel complete")
              println(c.requester)
              c.requester ! Response(0, "Success", responses)
              //state = TravelServiceState(state.tripRepository,QueryTravelState (state.queryTravelState.requests - (("QueryTravel", c.requester, c.RequestId))),
              //state.getTripAllDetailInfoState)
            }
          }

          if(c.requestLabel.equals("GetTripAllDetailInfo")){
            c.sender match {
              case Some(ref) =>  ref ! ResponseGetTripAllDetailInfo(c.deliveryId,c.requester,c.requestId,TripAllDetail(response,qstate.trip.get),found = true,label = c.label)
              case None =>  c.requester ! ResponseGetTripAllDetailInfo(c.deliveryId,c.requester,c.requestId,TripAllDetail(response,qstate.trip.get),found = true,label = c.label)
            }
            state = TravelServiceState(state.tripRepository,state.queryTravelState,
              GetTripAllDetailInfoState(state.getTripAllDetailInfoState.requests - (("GetTripAllDetailInfo",c.requester,c.requestId))))
          }
        }



    }


    override def receiveCommand: Receive = {
      case c: CreateTravel =>
        if (state.tripRepository.trips.contains(c.travelInfo.tripId)) {
          sender() ! Response(1, "Trip allready exist", None)
        }
        else {
          persistAsync(c)(updateState)
          sender() ! Response(0, "Success", None)
        }

      case RetrieveTravel(tripId: Int) =>
        val trip: Option[Trip] = state.tripRepository.trips.get(tripId)
        trip match {
          case Some(trp) =>
            sender() ! Response(0, "Success", trp)
          case None =>
            sender() ! Response(1, "No trip by that id", None)
        }
      case c: UpdateTravel =>
        state.tripRepository.trips.get(c.travelInfo.tripId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "No trip found", None)
        }

      case c: DeleteTravel =>
        state.tripRepository.trips.get(c.tripId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "No trip found", None)
        }
      case c: QueryTravel =>
        val startingPlaceId = queryForStationId(c.tripInfo.startingPlace).get
        val endPlaceId = queryForStationId(c.tripInfo.endPlace).get
        var trips: List[(Trip,Route)] = List()
        for (tripEntry <- state.tripRepository.trips) {
          val tempRoute = getRouteByRouteId(tripEntry._2.routeId).get
          if (tempRoute.stations.contains(startingPlaceId) &&
            tempRoute.stations.contains(endPlaceId) &&
            tempRoute.stations.indexOf(startingPlaceId) < tempRoute.stations.indexOf(endPlaceId)) {
            println("viable trip" + tripEntry._2)
            trips = (tripEntry._2 , tempRoute):: trips
            println("persisintng "+ c)
            persistAsync(c)(updateState)

          }
        }
        c.trip_route = trips
        persist(c)(updateState)
        trips.foreach({ a =>
          executeTickets(sender(),c.RequestId,"QueryTravel",a._1, a._2, startingPlaceId,
            endPlaceId, c.tripInfo.startingPlace, c.tripInfo.endPlace, c.tripInfo.departureTime, "",None)
        })


      case c: ResponseQueryForTravel =>
        persistAsync(c)(updateState)

      case c: QueryAlreadySoldOrders =>
        persist(c)(updateState)

      case c:ResponseQueryAlreadySoldOrders =>
        persist(c)(updateState)

      case c: QueryTravelComplete=>
        persist(c)(updateState)


      case c: GetTripAllDetailInfo =>
        state.tripRepository.trips.get(c.gtdi.tripId) match {
          case Some(trip) =>
            val startingPlaceName = c.gtdi.from
            val endPlaceName = c.gtdi.to
            val startingPlaceId = queryForStationId(startingPlaceName)
            val endPlaceId = queryForStationId(endPlaceName)
            val tempRoute = getRouteByRouteId(trip.routeId).get
            c.temRoute = Some(tempRoute)
            c.tempTrip = Some(trip)
            persist(c)(updateState)
            executeTickets(c.requester,c.requestId,"GetTripAllDetailInfo",trip, tempRoute, startingPlaceId.get,
              endPlaceId.get, startingPlaceName, endPlaceName, c.gtdi.travelDate, c.label,c.sender)
          case None =>
            sender() ! ResponseGetTripAllDetailInfo(c.deliveryId,c.requester,c.requestId,null, found=false, label = c.label,c.sender)
        }


      case GetRouteByTripId(tripId: Int) =>
        state.tripRepository.trips.get(tripId) match {
          case Some(trip) =>
            getRouteByRouteId(trip.routeId) match {
              case Some(route) => sender() ! Response(0, "Success", route)
              case None => sender() ! Response(1, "Failed", None)
            }
          case None => sender() ! Response(1, "No trip found", None)

        }


      case GetTrainTypeByTripId(tripId: Int) =>
        state.tripRepository.trips.get(tripId) match {
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
        sender() ! Response(0, "success", state.tripRepository.trips.values.toList)
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
        for (trip <- state.tripRepository.trips.values) {

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


    def executeTickets(requester: ActorRef, requestId: Int,requestLabel: String,trip: Trip, route: Route, startingPlaceId: Int, endPlaceId: Int, startingPlaceName: String,
                       endPlaceName: String, departureTime: Date, label: String, sender: Option[ActorRef]): Unit = {
      if (!afterToday(departureTime)) None
      println("TRIP: " + trip)
      println("Route: " + route)
      println("from: " + startingPlaceName + " " + startingPlaceId)
      println("to: " + endPlaceName + " " + endPlaceId)
      val travel = Travel(trip, startingPlaceName, endPlaceName, departureTime)
      deliver(trainTicketInfoService.path)(deliveryId => QueryForTravel(deliveryId, requester, requestId, travel, requestLabel,label=label, sender ))
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
      state.tripRepository.trips.values.filter(trip => trip.routeId == routeId).toList
    }

  }

}
