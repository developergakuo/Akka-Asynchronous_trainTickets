import TSAuthService.UsersServiceSate
import TSCommon.Commons._
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import akka.pattern.ask
implicit val timeout: Timeout = 2.seconds
import scala.util.{Success, Failure}
object TSBasicService {
  case class UserRepository(users: Map[Int,User])
  class BasicService extends PersistentActor {
    var state = UserRepository(users = Map())
    var stationService: ActorRef = null
    var trainService: ActorRef = null
    var routeService: ActorRef = null
    var priceService: ActorRef = null

    override def preStart(): Unit = {
      println("Client prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("Client post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "UserService-Client-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: UserRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("Client RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: SaveUser ⇒
        val id = state.users.size
        state = UserRepository( state.users + (id -> c.user))
      case c: DeleteUserByUserId =>
        state = UserRepository( state.users -  c.userId)
    }


    override def receiveCommand: Receive = {
      case c:QueryForTravel =>
        val startingPlaceExist = checkStationExists(c.travel.startingPlace)
        val endPlaceExist = checkStationExists(c.travel.endPlace)
        if (!startingPlaceExist || !endPlaceExist) {
          sender ! Response(1,"There is no travel route between the two stations",None)
        }else{
          val trainType:Option[TrainType] = queryTrainType(c.travel.trip.trainTypeId)
          trainType match {
            case Some(trntype) =>
              val routeId = c.travel.trip.routeId
              val trainTypeId = trntype.id
              val route = getRouteByRouteId(routeId).get
              val priceConfig = queryPriceConfigByRouteIdAndTrainType(routeId, trainTypeId).get
              val startingPlaceId = queryForStationId(c.travel.startingPlace).get
              val endPlaceId = queryForStationId(c.travel.endPlace).get
              val indexStart = route.stations.indexOf(startingPlaceId)
              val indexEnd = route.stations.indexOf(endPlaceId)
              var prices: Map[String, Double] = Map()
              try {
                val distance = route.distances(indexEnd) - route.distances(indexStart)
                val priceForEconomyClass = distance * priceConfig.basicPriceRate
                val priceForConfortClass = distance * priceConfig.firstClassPriceRate
                prices = prices + ("economyClass" -> priceForEconomyClass)
                prices = prices + ("confortClass" ->  priceForConfortClass)
              } catch {
                case  Exception =>
                  prices = prices + ("economyClass"-> 95.0)
                  prices = prices + ("confortClass"-> 120.0)
              }
              sender()! Response(0, "Success", TravelResult(status=true,0,trntype,prices,""))
            case None =>
              sender() ! Response(1, "Train type doesn't exist",None)
          }
        }


      case c: QueryForStationId =>
        queryForStationId(c.stationName) match {
          case Some(id)=> sender() ! Response(0, "Success", id)
          case None => sender() ! Response(1, "No Station Found", None)

        }


    }

    def queryForStationId(stationName: String): Option[Int] = {
      var staionId:Option[Int] = None
      val response: Future[Any] = stationService ? QueryForStationId(stationName)
      response onComplete {
        case Success(resp)=> if(resp.asInstanceOf[Response].status == 0)
          staionId = Some(resp.asInstanceOf[Response].data.asInstanceOf[Int])
        case Failure(_) => staionId = None
      }
      staionId
    }

    def checkStationExists(stationName: String): Boolean = {
      var exists = false
      val response: Future[Any] = stationService ? QueryForStationId(stationName)
      response onComplete {
        case Success(resp)=> if(resp.asInstanceOf[Response].status == 0) exists = true
        case Failure(_) => exists = false
      }
      exists
      }

    def queryTrainType(trainTypeId: Int): Option[TrainType] = {
      var trainType: Option[TrainType] = None
      val response: Future[Any] = trainService ? GetTrainType(trainTypeId)
      response onComplete {
        case Success(resp)=>
          if(resp.asInstanceOf[Response].status == 0)
            trainType = Some(resp.asInstanceOf[Response].data.asInstanceOf[TrainType])
          else trainType = None
        case Failure(_) => trainType = None
      }
      trainType
    }

    private def getRouteByRouteId(routeId: Int):Option[Route] = {
      var route: Option[Route] = None
      val response: Future[Any] = routeService ? GetRouteById(routeId)
      response onComplete {
        case Success(resp)=>
          if(resp.asInstanceOf[Response].status == 0)
            route = Some(resp.asInstanceOf[Response].data.asInstanceOf[Route])
          else route = None
        case Failure(_) => route = None
      }
      route
    }

    private def queryPriceConfigByRouteIdAndTrainType(routeId: Int, trainType: Int):Option[PriceConfig] = {
      var priceConfig: Option[PriceConfig] = None
      val response: Future[Any] = priceService ? QueryPriceConfigByRouteIdAndTrainType(routeId,trainType)
      response onComplete {
        case Success(resp)=>
          if(resp.asInstanceOf[Response].status == 0)
            priceConfig = Some(resp.asInstanceOf[Response].data.asInstanceOf[PriceConfig])
          else priceConfig = None
        case Failure(_) => priceConfig = None
      }
      priceConfig
    }

  }

}
