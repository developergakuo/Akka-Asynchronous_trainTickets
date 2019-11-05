
import TSCommon.Commons._
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}

object TSTrainService {



  case class TrainRepository(trains: Map[Int, TrainType])

  class TrainService extends PersistentActor {
    var state: TrainRepository = TrainRepository(Map())

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
      case SnapshotOffer(_, offeredSnapshot: TrainRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateTrain ⇒
        state = TrainRepository(state.trains + (c.trainType.id -> c.trainType))
      case c: DeleteTrain =>
        state = TrainRepository(state.trains - c.id)
    }


    override def receiveCommand: Receive = {

      case c:CreateTrain =>
        state.trains.get(c.trainType.id) match {
          case Some(_) =>
            sender() ! Response(1,"A train type with a similar id exists ",None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",None)
        }

      case RetrieveTrain(id: Int) =>
        state.trains.get(id) match {
          case Some(typ) =>
            sender() ! Response(0,"Success",typ)
          case None =>
            sender() ! Response(1,"No train type by that id",None)
        }

      case c: UpdateTrain =>
        persist(CreateTrain(c.trainType))(updateState)
        sender() ! Response(0,"Success",None)

      case c:DeleteTrain =>
        state.trains.get(c.id) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.id)
          case None =>
            sender() ! Response(1,"No train type by that id",None)
        }

      case QueryTrains() =>
        sender() ! Response(0,"Success",state.trains.values.toList)

    }

  }

}
