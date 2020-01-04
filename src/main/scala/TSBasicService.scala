import TSCommon.Commons._
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.Await
import akka.pattern.ask

import scala.util.Random
object TSBasicService {
  case class UserRepository(users: Map[Int,User])
  class BasicService (stationService: ActorRef , trainService: ActorRef , routeService: ActorRef , priceService: ActorRef) extends PersistentActor {
    var state = UserRepository(users = Map())

    override def preStart(): Unit = {
      println("Client prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("Client post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "BasicService-id"

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

        //cut it here
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
                case  _:Exception =>
                  prices = prices + ("economyClass"-> 95.0)
                  prices = prices + ("confortClass"-> 120.0)
              }
              println("========== BasicService:QueryForTravel: Success ")
              sender()! Response(0, "Success", TravelResult(status=true,0,trntype,prices,""))
            case None =>
              sender() ! Response(1, "Train type doesn't exist",None)
          }
        }


      case c: QueryForStationId =>
        println("========== BasicService:QueryForStationId ")
        queryForStationId(c.stationName) match {
          case Some(id)=>
            println("========== BasicService:QueryForStationId: Success ")
            sender() ! Response(0, "Success", id)
          case None => sender() ! Response(1, "No Station Found", None)

        }


    }

    def queryForStationId(stationName: String): Option[Int] = {
      var staionId:Option[Int] = None
      val responseFuture: Future[Any] = stationService ? QueryForIdStation(0,self,Random.nextInt(1000),stationName,-1)
      val response = Await.result(responseFuture,duration).asInstanceOf[ResponseQueryForIdStation]
      if(response.found) staionId = Some(response.stationId)
      staionId
    }

    def checkStationExists(stationName: String): Boolean = {
      var exists = false
      val responseFuture: Future[Any] = stationService ? QueryForIdStation(0,self,Random.nextInt(1000),stationName,-1)
      val response = Await.result(responseFuture,duration).asInstanceOf[ResponseQueryForIdStation]
      if(response.found) exists = true
      exists
      }

    def queryTrainType(trainTypeId: Int): Option[TrainType] = {
      println("========== BasicService:queryTrainType ")
      var trainType: Option[TrainType] = None
      val responseFuture: Future[Any] = trainService ? RetrieveTrain(trainTypeId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if(response.status == 0){
        println("========== BasicService:queryTrainType: success")
        trainType = Some(response.data.asInstanceOf[TrainType])
      }
      trainType
    }

    private def getRouteByRouteId(routeId: Int):Option[Route] = {
      var route: Option[Route] = None
      val responseFuture: Future[Any] = routeService ? GetRouteById(routeId)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if(response.status == 0) {
        println("========== BasicService:getRouteByRouteId: success ")
        route = Some(response.data.asInstanceOf[Route])
      }
      route
    }

    private def queryPriceConfigByRouteIdAndTrainType(routeId: Int, trainType: Int):Option[PriceConfig] = {
      var priceConfig: Option[PriceConfig] = None
      val responseFuture: Future[Any] = priceService ? FindByRouteIdAndTrainType(routeId,trainType)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if(response.status == 0) priceConfig = Some(response.data.asInstanceOf[PriceConfig])
      priceConfig
    }

  }

}
