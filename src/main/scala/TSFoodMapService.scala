


import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}

object TSFoodMapService {

  case class FoodRepository(foodStore: Map[Int, FoodStore], trainFood: Map[Int, TrainFood])

  class FoodMapService extends PersistentActor {
    var state: FoodRepository = FoodRepository(Map(),Map())

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
      case SnapshotOffer(_, offeredSnapshot: FoodRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateFoodStore ⇒ state = FoodRepository(state.foodStore + (c.fs.id -> c.fs),state.trainFood)
      case c: CreateTrainFood ⇒ state = FoodRepository(state.foodStore, state.trainFood + (c.tf.id -> c.tf))
    }

    override def receiveCommand: Receive = {
      case c:CreateFoodStore =>
        state.foodStore.get(c.fs.id) match {
          case Some(_) =>
            sender() ! Response(1, "Error: Store exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Added", None)
        }


      case c:CreateTrainFood =>
        state.trainFood.get(c.tf.id) match {
          case Some(_) =>
            sender() ! Response(1, "Error: Store exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Added", None)
        }

      case ListFoodStores =>
       sender() ! Response(0, "Success",state.foodStore.values.toList)

      case ListTrainFood =>
        sender() ! Response(0, "Success",state.trainFood.values.toList)

      // query according id
      case ListFoodStoresByStationId(stationId: Int) =>
        sender() ! Response(0, "Success",state.foodStore.values.filter(store => store.stationId == stationId).toList)

      case ListTrainFoodByTripId(tripId: Int) =>
        sender() ! Response(0, "Success",state.trainFood.values.filter(food => food.tripId == tripId).toList)

      case GetFoodStoresByStationIds(stationIds: List[Int]) =>
        sender() ! Response(0, "Success",state.foodStore.values.filter(store =>
          stationIds.contains(store.stationId)).toList)
    }
  }
}