import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await

import scala.collection.mutable.ListBuffer

import TSCommon.Commons
object TSRoutePlanService {
  class RoutePlanService ( orderService: ActorRef , orderOtherService: ActorRef , configService: ActorRef , travelService: ActorRef ,
                             travel2Service: ActorRef , stationService: ActorRef , routeService: ActorRef) extends Actor {
    override def receive: Receive = {
      case c:SearchCheapestResult =>
        println("======== routeplan: SearchCheapest: ")
        val tripInfo = TripInfo(c.info.fromStationName,c.info.toStationName,c.info.travelDate)
        val highSpeed = getTripFromHighSpeedTravelService(tripInfo)
        val normalTrain = getTripFromNormalTrainTravelService(tripInfo)
        val finalResult:scala.collection.mutable.ListBuffer[TripResponse] = (highSpeed ++ normalTrain).to[ListBuffer]
        var minPrice = .0
        var minIndex = -1
        val size = Math.min(5, finalResult.size)
        var returnResult: List[TripResponse] = List()
        var i = 0
        println("======== routeplan: FinalResult"+ finalResult)
        while (i < size) {
          minPrice = Float.MaxValue
          var j = 0
          while ( {
            j < finalResult.size
          }) {
            val thisRes = finalResult(j)
            if (thisRes.priceForEconomyClass < minPrice) {
              minPrice = finalResult(j).priceForEconomyClass
              minIndex = j
            }

            {
              j += 1; j - 1
            }
          }
          returnResult = finalResult(minIndex)::returnResult
          finalResult.remove(minIndex)

          {
            i += 1; i - 1
          }
        }
        var units: List[RoutePlanResultUnit] = List()
        var i2 = 0
        while ( {
          i2 < returnResult.size
        }) {
          val tempResponse = returnResult(i2)
          val tempUnit = RoutePlanResultUnit(tempResponse.tripId,tempResponse.trainTypeId,tempResponse.startingStation,tempResponse.terminalStation,
            getStationList(tempResponse.tripId),tempResponse.priceForEconomyClass,tempResponse.priceForConfortClass,tempResponse.startingTime,tempResponse.endTime)

          units = tempUnit::units

          {
            i2 += 1; i2 - 1
          }
        }
        println("======== routeplan:cheapest Success: ")
        sender() ! Response(0, "Success", units.reverse)

      case  c:SearchQuickestResult=>
        val tripInfo = TripInfo(c.info.fromStationName,c.info.toStationName,c.info.travelDate)
        val highSpeed = getTripFromHighSpeedTravelService(tripInfo)
        val normalTrain = getTripFromNormalTrainTravelService(tripInfo)
        val finalResult: ListBuffer[TripResponse] = (highSpeed ++ normalTrain).to[ListBuffer]

        var minTime = 0L
        var minIndex = -1
        val size = Math.min(finalResult.size, 5)
        var returnResult: List[TripResponse] = List()
        var i = 0
        while ( {
          i < size
        }) {
          minTime = Long.MaxValue
          var j = 0
          while ( {
            j < finalResult.size
          }) {
            val thisRes = finalResult(j)
            if (thisRes.endTime.getTime - thisRes.startingTime.getTime < minTime) {
              minTime = thisRes.endTime.getTime - thisRes.startingTime.getTime
              minIndex = j
            }

            {
              j += 1; j - 1
            }
          }
          returnResult = finalResult(minIndex) :: returnResult
          finalResult.remove(minIndex)

          {
            i += 1; i - 1
          }
        }
        var units: List[RoutePlanResultUnit] = List()
        var i2 = 0
        while ( {
          i2 < returnResult.size
        }) {
          val tempResponse = returnResult(i2)
          val tempUnit = RoutePlanResultUnit(tempResponse.tripId,tempResponse.trainTypeId,tempResponse.startingStation,tempResponse.terminalStation,
            getStationList(tempResponse.tripId),tempResponse.priceForEconomyClass,tempResponse.priceForConfortClass,tempResponse.startingTime,tempResponse.endTime)

          units = tempUnit::units

          {
            i2 += 1; i2 - 1
          }
        }
        sender() ! Response(0, "Success", units)

      case c: SearchMinStopStations =>
        val fromStationId = queryForStationId(c.info.fromStationName)
        val toStationId = queryForStationId(c.info.toStationName)


        var routeListResp: Option[List[Route]] = None
        val responseFuture: Future[Any] = routeService ? GetRouteByStartAndTerminal(fromStationId,toStationId)
        val response = Await.result(responseFuture,duration).asInstanceOf[Response]
        if (response.status == 0) routeListResp = Some(response.data.asInstanceOf[List[Route]])

        val routeList = routeListResp.get.to[ListBuffer]
        var gapList: ListBuffer[Integer] = ListBuffer()
        var i = 0
        while ( {i < routeList.size}) {
          val indexStart = routeList(i).stations.indexOf(fromStationId)
          val indexEnd = routeList(i).stations.indexOf(toStationId)
            gapList.+=(indexEnd - indexStart)

          {
            i += 1; i - 1
          }
        }

        var resultRoutes:List[Int] = List()
        val size = Math.min(5, routeList.size)
        var i2 = 0
        while ( {
          i2 < size
        }) {
          var minIndex = 0
          var tempMinGap = Integer.MAX_VALUE
          var j = 0
          while ( {
            j < gapList.size
          }) {
            if (gapList(j) < tempMinGap) {
              tempMinGap = gapList(j)
              minIndex = j
            }

            {
              j += 1; j - 1
            }
          }
          resultRoutes = routeList(minIndex).id :: resultRoutes
          routeList.remove(minIndex)
          gapList.remove(minIndex)

          {
            i2 += 1; i2- 1
          }
        }


        //request for trips from route ids

        var travelTrips: Option[List[Trip]] = None
        val response2Future: Future[Any] = travelService ? GetTripByRoute(resultRoutes)
        val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
        if (response2.status == 0) travelTrips = Some(response2.data.asInstanceOf[List[Trip]])

        var travel2Trips: Option[List[Trip]] = None
        val response3Future: Future[Any] = travel2Service ? GetTripByRoute(resultRoutes)
        val response3 = Await.result(response3Future,duration).asInstanceOf[Response]
        if (response3.status == 0) travel2Trips = Some(response3.data.asInstanceOf[List[Trip]])

        val finalTripResult: List[Trip] = travelTrips.get++travel2Trips.get
        var routePlanResultUnist: List[RoutePlanResultUnit] = List()
        for (trip <- finalTripResult) {
           val allDetailInfo = TripAllDetailInfo(trip.tripId,c.info.travelDate,
             c.info.fromStationName,c.info.toStationName)
          var service: ActorRef = null
          if (trip.tripId == 1|| trip.tripId == 2) service = travelService
          else service = travel2Service
          var tripAllDetail: Option[TripAllDetail] = None
          val responseFuture: Future[Any] = service ? GetTripAllDetailInfo(allDetailInfo)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) tripAllDetail = Some(response.data.asInstanceOf[TripAllDetail])
          val tripResponse: TripResponse = tripAllDetail.get.tripResponse
          val unit = RoutePlanResultUnit(trip.tripId,tripResponse.trainTypeId,tripResponse.startingStation,
               tripResponse.terminalStation,getRouteByRouteId(trip.tripId).stations,tripResponse.priceForEconomyClass
               ,tripResponse.priceForConfortClass,tripResponse.startingTime,tripResponse.endTime)
          routePlanResultUnist = unit :: routePlanResultUnist
        }
        sender() ! Response(0, "Success.", routePlanResultUnist)
    }

    private def queryForStationId(stationName: String): Int = {
      var result: Option[Int] = None
      val responseFuture: Future[Any] = stationService ? QueryForStationId(stationName)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.asInstanceOf[Response].status == 0) result = Some(response.data.asInstanceOf[Int])
      result.get
    }

    private def getRouteByRouteId(routeId: Int): Route = {
      var result: Option[Route] = None
      val responseFuture: Future[Any] = routeService ? GetRouteById(routeId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.asInstanceOf[Response].status == 0) result = Some(response.data.asInstanceOf[Route])
      result.get
    }

    private def getTripFromHighSpeedTravelService(info: TripInfo):List[TripResponse]  = {
      println("============ getTripFromHighSpeedTravelService")
      var result: Option[List[TripResponse]]= None
      val responseFuture: Future[Any] = travelService ? QueryTravel(info)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) {
        ("============ getTripFromHighSpeedTravelService: Success")
        result = Some(response.data.asInstanceOf[List[TripResponse]])
      }

      result.get
    }

    private def getTripFromNormalTrainTravelService(info: Commons.TripInfo):List[TripResponse] = {
      var result: Option[List[TripResponse]]= None
      val responseFuture: Future[Any] = travel2Service ? QueryTravel(info)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) result = Some(response.data.asInstanceOf[List[TripResponse]])
      result.get
    }

    private def getStationList(tripId: Int): List[Int] = {
      var service: ActorRef = null
      if (tripId == 1 || tripId == 2) service = travelService else service = travel2Service
      var result: Option[Route]= None
      val responseFuture: Future[Any] = service ? GetRouteByTripId(tripId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) result = Some(response.asInstanceOf[Response].data.asInstanceOf[Route])
      result.get.stations
    }
  }
}
