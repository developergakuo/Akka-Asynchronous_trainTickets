import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import scala.collection.mutable.ListBuffer
object TSConfigService {
  case class ConfigRepository(configs: ListBuffer[Config])

  class ConfigService extends PersistentActor {
    var state: ConfigRepository = ConfigRepository(ListBuffer(Config("DirectTicketAllocationProportion",0.5,
      "Allocation Proportion Of The Direct Ticket - From Start To End")))

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
      case SnapshotOffer(_, offeredSnapshot: ConfigRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateConfig ⇒ state.configs.+=(c.info)
      case c: DeleteConfig2 => state.configs.remove(c.index)
      case c: Update2 =>
        state.configs.remove(c.index)
        state.configs.+=(c.info)


    }

    override def receiveCommand: Receive = {
      case c: CreateConfig =>
        getConfigByName(c.info.name) match {
          case Some(_) =>
            sender() ! Response(1, "Error: Config with a similar name exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Config added", None)
        }

      case  c:Update =>
        getConfigByName(c.info.name) match {
          case Some(index) =>
            persist(Update2(index,c.info))(updateState)
            sender() ! Response(0, "Success: Config updated", None)
          case None =>
            sender() ! Response(1, "Error: Config with that name does not exist", None)
        }

      case  c:Query =>
        getConfigByName(c.name) match {
          case Some(index) =>
           val config: Config = state.configs(index)
            sender() ! Response(0, "Success: Config found", config)
          case None =>
            sender() ! Response(1, "Error: Config with that name does not exist", None)
        }

      case  c:Delete =>
        getConfigByName(c.name) match {
          case Some(index) =>
            persist(DeleteConfig2(index))(updateState)
            sender() ! Response(0, "Success: Config deleted", None)
          case None =>
            sender() ! Response(1, "Error: Config with that name does not exist", None)
        }

      case  QueryAll =>
        sender() ! Response(0, "Success: Configs", state.configs.toList)
    }

    def getConfigByName(configName: String): Option[Int] ={
      var index: Option[Int] = None
      var i = 0
     for (config <- state.configs){
       if (config.name == configName) index = Some(i)
       i =i+1
     }
      index
    }
  }
}





