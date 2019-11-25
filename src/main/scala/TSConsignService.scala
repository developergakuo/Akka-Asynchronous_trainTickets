
import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await

import akka.util.Timeout

object TSConsignService {

  case class ConfigRepository(consigns: Map[Int, ConsignRecord])

  class ConsignService(consignPriceService: ActorRef ) extends PersistentActor {
    var state: ConfigRepository = ConfigRepository(Map())


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
      case c: InsertConsignRecord2 ⇒ state = ConfigRepository(state.consigns + (c.consignRecord.id->c.consignRecord))
    }

    override def receiveCommand: Receive = {
      case c:InsertConsignRecord =>
        state.consigns.get(c.consignRequest.id) match{
          case Some(_) =>
            sender() ! Response(1, "Error: consignment with similar ID exists", None )

          case None =>
            var price: Option[Double]= None
            val responseFuture: Future[Any] = consignPriceService ? GetPriceByWeightAndRegion(c.consignRequest.weight,c.consignRequest.isWithin)
            val response = Await.result(responseFuture,duration).asInstanceOf[Response]
            if (response.status == 0) price =Some(response.data.asInstanceOf[Double])
            price match {
              case Some(prix) =>
                val consignRecord = ConsignRecord(c.consignRequest.id,c.consignRequest.orderId,c.consignRequest.accountId,c.consignRequest.handleDate,
                  c.consignRequest.targetDate,c.consignRequest.from,c.consignRequest.to,c.consignRequest.consignee,c.consignRequest.phone,c.consignRequest.weight,prix)
                persist(InsertConsignRecord2(consignRecord))(updateState)
                sender() ! Response(0, "Success: Added", None )
              case None =>
                sender() ! Response(1, "Error: not Added, try later", None )
            }
        }

      case c:UpdateConsignRecord =>
        state.consigns.get(c.consignRequest.id) match{
          case Some(_) =>
            var price: Option[Double]= None
            val responseFuture: Future[Any] = consignPriceService ? GetPriceByWeightAndRegion(c.consignRequest.weight,c.consignRequest.isWithin)
            val response = Await.result(responseFuture,duration).asInstanceOf[Response]
            if (response.status == 0) price =Some(response.data.asInstanceOf[Double])
            price match {
              case Some(prix) =>
                val consignRecord = ConsignRecord(c.consignRequest.id,c.consignRequest.orderId,c.consignRequest.accountId,c.consignRequest.handleDate,
                  c.consignRequest.targetDate,c.consignRequest.from,c.consignRequest.to,c.consignRequest.consignee,c.consignRequest.phone,c.consignRequest.weight,prix)
                persist(InsertConsignRecord2(consignRecord))(updateState)
                sender() ! Response(0, "Success: Added", None )
              case None =>
                sender() ! Response(1, "Error: not Added, try later", None )
            }

          case None =>
            sender() ! Response(1, "Error: consignment  does not exist", None )
        }

      case c:QueryByAccountId =>
        val consignRecord: ConsignRecord = state.consigns.values.filter(consignment =>consignment.accountId == c.accountId).head
        if(consignRecord != null){
          sender() ! Response(0, "Success", consignRecord)
        }else{
          sender() ! Response(1, "Error: Not found", None )
        }

      case c:QueryByOrderId =>
        val consignRecord: ConsignRecord = state.consigns.values.filter(consignment =>consignment.orderId == c.orderId).head
        if(consignRecord != null){
          sender() ! Response(0, "Success", consignRecord)
        }else{
          sender() ! Response(1, "Error: Not found", None )
        }

      case c:QueryByConsignee =>
        val consignRecord: Iterable[ConsignRecord] = state.consigns.values.filter(consignment =>consignment.consignee == c.consignee)
        if(consignRecord.nonEmpty){
          sender() ! Response(0, "Success", consignRecord.head)
        }else{
          sender() ! Response(1, "Error: Not found", None )
        }
    }

  }
}