import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scala.collection.mutable.ListBuffer

implicit val timeout: Timeout = 2.seconds
import TSCommon.Commons
object TSRoutePlanService {
  class TSRoutePlanService extends Actor {
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null
    var configService: ActorRef = null
    var travelService: ActorRef = null
    var travel2Service: ActorRef = null
    var stationService: ActorRef = null
    var routeService: ActorRef = null
    override def receive: Receive = {
      case c:SearchCheapestResult =>
        val tripInfo = TripInfo(c.info.fromStationName,c.info.toStationName,c.info.travelDate)
        val highSpeed = getTripFromHighSpeedTravelService(tripInfo)
        val normalTrain = getTripFromNormalTrainTravelService(tripInfo)
        val finalResult:scala.collection.mutable.ListBuffer[TripResponse] = (highSpeed ++ normalTrain).to[ListBuffer]
        var minPrice = .0
        var minIndex = -1
        val size = Math.min(5, finalResult.size)
        var returnResult: List[TripResponse] = List()
        var i = 0
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
        val response: Future[Any] = routeService ? GetRouteByStartAndTerminal(fromStationId,toStationId)
        response onComplete {
          case Success(resp: Response) =>
            if (resp.status == 0) routeListResp = Some(resp.data.asInstanceOf[List[Route]])
          case Failure(_) =>
            routeListResp = None
        }
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
        val response2: Future[Any] = travelService ? GetTripByRoute(resultRoutes)
        response2 onComplete {
          case Success(resp: Response) =>
            if (resp.status == 0) travelTrips = Some(resp.data.asInstanceOf[List[Trip]])
          case Failure(_) =>
            travelTrips = None
        }
        var travel2Trips: Option[List[Trip]] = None
        val response3: Future[Any] = travel2Service ? GetTripByRoute(resultRoutes)
        response3 onComplete {
          case Success(resp: Response) =>
            if (resp.status == 0) travel2Trips = Some(resp.data.asInstanceOf[List[Trip]])
          case Failure(_) =>
            travel2Trips = None
        }
        val finalTripResult: List[Trip] = travelTrips.get++travel2Trips.get
        var routePlanResultUnist: List[RoutePlanResultUnit] = List()
        for (trip <- finalTripResult) {
           val allDetailInfo = TripAllDetailInfo(trip.tripId,c.info.travelDate,
             c.info.fromStationName,c.info.toStationName)
          var service: ActorRef = null
          if (trip.tripId == 1|| trip.tripId == 2) service = travelService
          else service = travel2Service
          var tripAllDetail: Option[TripAllDetail] = None
          val response: Future[Any] = service ? GetTripAllDetailInfo(allDetailInfo)
          response onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) tripAllDetail = Some(resp.data.asInstanceOf[TripAllDetail])
            case Failure(_) =>
              tripAllDetail = None
          }
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
      val response: Future[Any] = stationService ? QueryForStationId(stationName)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Int])
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get
    }

    private def getRouteByRouteId(routeId: Int): Route = {
      var result: Option[Route] = None
      val response: Future[Any] = routeService ? GetRouteById(routeId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Route])
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get
    }

    private def getTripFromHighSpeedTravelService(info: TripInfo):List[TripResponse]  = {
      var result: Option[List[TripResponse]]= None
      val response: Future[Any] = travelService ? QueryTravel(info)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[List[TripResponse]])
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get

    }

    private def getTripFromNormalTrainTravelService(info: Commons.TripInfo):List[TripResponse] = {
      var result: Option[List[TripResponse]]= None
      val response: Future[Any] = travel2Service ? QueryTravel(info)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[List[TripResponse]])
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get
    }

    private def getStationList(tripId: Int): List[Int] = {
      var service: ActorRef = null
      if (tripId == 1 || tripId == 2) service = travelService else service = travel2Service
      var result: Option[Route]= None
      val response: Future[Any] = service ? GetRouteByTripId(tripId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Route])
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get.stations
    }
  }
}
