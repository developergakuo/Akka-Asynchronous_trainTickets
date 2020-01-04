import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import InputData._

object TSPriceService {
  case class PriceConfigRepository(configs: Map[Int, PriceConfig])
  class PriceService extends PersistentActor {
    var state: PriceConfigRepository = PriceConfigRepository(priceConfigs.zipWithIndex.map(a=>a._2+1 -> a._1).toMap)


    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "PriceService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: PriceConfigRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateNewPriceConfig ⇒
        state = PriceConfigRepository(state.configs + (c.priceConfig.id -> c.priceConfig))

      case c: UpdatePriceConfig =>
        state = PriceConfigRepository(state.configs + (c.priceConfig.id -> c.priceConfig))
      case c: DeletePriceConfig =>
        state = PriceConfigRepository(state.configs - c.priceConfig.id)

    }

    override def receiveCommand: Receive = {
      case c:CreateNewPriceConfig =>
        state.configs.get(c.priceConfig.id) match {
          case Some(_) =>
            sender() ! Response(1, "Create failure: Config with similar id exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Create success", None)
        }

      case c:FindById =>
        state.configs.get(c.id) match {
          case Some(priceConfig) =>
            sender() ! Response(0, "Success", priceConfig)
          case None =>
            persist(c)(updateState)
            sender() ! Response(1, "No priceConfig found", None)
        }
      case c:FindByRouteIdAndTrainType =>
        println("===========PriceService: ")
        var config:Option[PriceConfig] = None
        for (config1 <-state.configs.values){
          if(config1.routeId == c.routeId && config1.trainType== c.trainType)  config = Some(config1)
        }
        config match {
          case Some(config1) =>
            println("===========PriceService: success")
            sender() ! Response(0,"Success", config1)
          case None =>
        sender() ! Response(1,"Failure: No matching config", config)
        }
      case FindAllPriceConfig =>
        sender() ! Response(0, "Success",state.configs.values.toList)
      case c:DeletePriceConfig =>
        state.configs.get(c.priceConfig.id) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Delete success", None)
          case None =>
            sender() ! Response(1, "Delete failure: Config not found", None)
        }
      case c:UpdatePriceConfig =>
        state.configs.get(c.priceConfig.id) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Update success", None)
          case None =>
            sender() ! Response(1, "Update failure: Config not found", None)
        }
    }

  }

}



