
import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}



object TSPaymentService {
  case class PaymentRepository(moneys: Map[Int, Money], payments: Map[Int,Payment])

  class PaymentService extends PersistentActor {
    var state: PaymentRepository = PaymentRepository(Map(),Map())

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
      case SnapshotOffer(_, offeredSnapshot: PaymentRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: AddMoney2 ⇒
        val userPreviousSum  = state.moneys.get(c.payment.userId).get.money
        state = PaymentRepository(state.moneys + (c.payment.userId -> Money(c.payment.userId,c.payment.price+userPreviousSum)),state.payments)
      case c: InitPayment =>
        state = PaymentRepository(state.moneys,state.payments + (c.payment.Id -> c.payment))
      case c: Pay =>
        state = PaymentRepository(state.moneys,state.payments  + (c.info.orderId -> Payment(-1,c.info.orderId,c.info.userId,c.info.price)))

    }

    override def receiveCommand: Receive = {
      case c:Pay =>
        state.payments.get(c.info.orderId) match {
          case Some(_) =>
            sender() ! Response(1, "Error: Payment already exists",None )

          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Payment added",None )
        }
      case c:AddMoney2 =>
        persist(c)(updateState)
        sender() ! Response(0,"Success", None)
      case Query2 =>
        sender() ! Response(0, "Success",state.payments)
      case c:InitPayment =>
        state.payments.get(c.payment.Id) match {
          case Some(_)=> // do nothing
            sender() ! Response(1, "Error: Payment already exists",None )
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Payment added",None )

        }

    }

  }

}




