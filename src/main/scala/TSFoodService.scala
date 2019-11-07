
import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}

import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global


implicit val timeout: Timeout = 2.seconds
object TSFoodService {
  case class FoodOrderRepository(orders: Map[Int, FoodOrder])

  class FoodService extends PersistentActor {
    var state: FoodOrderRepository = FoodOrderRepository(Map())
    var foodMapService: ActorRef = null
    var travelService: ActorRef = null
    var stationService: ActorRef = null

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
      case SnapshotOffer(_, offeredSnapshot: FoodOrderRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateFoodOrder ⇒ state = FoodOrderRepository(state.orders + (c.afoi.id -> c.afoi))
      case c: DeleteFoodOrder => state = FoodOrderRepository(state.orders - c.orderId)
    }

    override def receiveCommand: Receive = {
      case c:CreateFoodOrder=>
        state.orders.get(c.afoi.id) match {
          case Some(_) =>
            sender () ! Response(1, "Error: order id already exists", None)
          case None=>
            persist(c)(updateState)
            sender() ! Response(0, "Success: added", None)
        }

      case c:DeleteFoodOrder =>
        state.orders.get(c.orderId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Deleted", None)
          case None=>
            sender () ! Response(1, "Error: order id does not exist", None)
        }

      case c:FindByOrderId =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            sender() ! Response(0, "Success: Updated", order)
          case None=>
            sender () ! Response(1, "Error: order id does not exist", None)
        }

      case c:UpdateFoodOrder =>
        state.orders.get(c.updateFoodOrder.id) match {
          case Some(_) =>
            persist(CreateFoodOrder(c.updateFoodOrder))(updateState)
            sender() ! Response(0, "Success: Updated", None)
          case None=>
            sender () ! Response(1, "Error: order id does not exist", None)
        }

      case FindAllFoodOrder =>
        sender() ! Response(0, "Success: Updated", state.orders.values.toList)

      case c:GetAllFood =>
        var trainFoods: Option[List[TrainFood]]= None
        val response: Future[Any] = foodMapService ? ListTrainFoodByTripId(c.tripId)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) trainFoods =Some(res.asInstanceOf[Response].data.asInstanceOf[List[TrainFood]])
          case Failure(_) =>
            trainFoods = None
        }
        trainFoods match {
          case Some(trainFds) =>
            var route: Option[Route]= None
            val response: Future[Any] = travelService ? GetRouteByTripId(c.tripId)
            response onComplete {
              case Success(res) =>
                if (res.asInstanceOf[Response].status == 0) route = Some(res.asInstanceOf[Response].data.asInstanceOf[Route])
              case Failure(_) =>
                route = None
            }
            route match {
              case Some(r) =>
                var startStatioId: Option[Int]= None
                val response: Future[Any] = stationService ? QueryForIdStation(c.startStation)
                response onComplete {
                  case Success(res) =>
                    if (res.asInstanceOf[Response].status == 0) startStatioId = Some(res.asInstanceOf[Response].data.asInstanceOf[Int])
                  case Failure(_) =>
                    startStatioId = None
                }

                var endStationId: Option[Int]= None
                val response2: Future[Any] = stationService ? QueryForIdStation(c.endStation)
                response2 onComplete {
                  case Success(res) =>
                    if (res.asInstanceOf[Response].status == 0) endStationId = Some(res.asInstanceOf[Response].data.asInstanceOf[Int])
                  case Failure(_) =>
                    endStationId = None
                }
                val stations: List[Int] = r.stations
                val start = stations.indexOf(startStatioId.get)
                val end = stations.indexOf(endStationId.get)
                val remainingStations = stations.slice(start,end)

                var foodStores: Option[List[FoodStore]]= None
                val response3: Future[Any] = foodMapService ? GetFoodStoresByStationIds(remainingStations)
                response3 onComplete {
                  case Success(res) =>
                    if (res.asInstanceOf[Response].status == 0) foodStores = Some(res.asInstanceOf[Response].data.asInstanceOf[List[FoodStore]])
                  case Failure(_) =>
                    foodStores = None
                }

                sender()  ! Response(0, "Success", (foodStores.get,trainFds))


              case None =>
                sender() ! Response(1,"Error: No route  data was  obtained", None)

            }


          case None =>
            sender() ! Response(1,"Error: No train foods obtained", None)
        }

    }

  }
}