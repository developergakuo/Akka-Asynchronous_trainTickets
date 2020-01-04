import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
object TSConsignPriceService {
  case class ConfigRepository(consigns: Map[Int, ConsignPrice])

  class ConsignPriceService extends PersistentActor {
    var state: ConfigRepository = ConfigRepository(Map(0->ConsignPrice(0,0,0.2,5,3.0,2.0)))

    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "ConsignPriceService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: ConfigRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateAndModifyPrice ⇒ state = ConfigRepository(state.consigns + (c.config.id->c.config))
    }

    override def receiveCommand: Receive = {
      case c:GetPriceByWeightAndRegion =>

        val priceConfig: ConsignPrice = state.consigns(0)
        var price = 0.0
        val initialPrice = priceConfig.initialPrice
        if (c.weight <= priceConfig.initialWeight) price = initialPrice
        else {
          val extraWeight = c.weight - priceConfig.initialWeight
          if (c.isWithinRegion) price = initialPrice + extraWeight * priceConfig.withinPrice
          else price = initialPrice + extraWeight * priceConfig.beyondPrice
        }
        sender() !  Response(0, "Success", price)

      case QueryPriceInformation =>
        sender ! Response(0, "Success", state.consigns.values.toList.head.toString)

      case c:CreateAndModifyPrice =>
        persist(c)(updateState)
        sender ! Response(0, "Success", None)


      case GetPriceConfig =>
        sender() ! Response(0,"Success", state.consigns.values.toList.head)
    }

  }
}





