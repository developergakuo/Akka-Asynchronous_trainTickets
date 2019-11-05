import TSCommon.Commons._
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import java.util. Date

implicit val timeout: Timeout = 2.seconds

object TSSecurityService {
  case class SecurityRepository(configs: Map[Int, SecurityConfig])

  class SecurityService extends PersistentActor {
    var state: SecurityRepository = SecurityRepository(Map())
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null

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
      case SnapshotOffer(_, offeredSnapshot: SecurityRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: AddNewSecurityConfig ⇒
        state = SecurityRepository(state.configs + (c.info.id -> c.info))

      case c: ModifySecurityConfig =>
        state = SecurityRepository(state.configs + (c.info.id -> c.info))
      case c: DeleteSecurityConfig =>
        state = SecurityRepository(state.configs - c.id)

    }


    override def receiveCommand: Receive = {
      case FindAllSecurityConfig =>
        sender() ! Response(0, " Success", state.configs)

      case c:AddNewSecurityConfig =>
        state.configs.get(c.info.id) match{
          case Some(_) =>
            sender() ! Response(1, "Config already exist", None)
          case None =>
            persistAsync(c)(updateState)
            sender() ! Response(0, "Success", None)
        }

      case c:ModifySecurityConfig=>
        state.configs.get(c.info.id) match{
          case Some(_) =>
            persistAsync(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "Config does not exist", None)
        }

      case c:DeleteSecurityConfig =>
        state.configs.get(c.id) match{
          case Some(_) =>
            persistAsync(c)(updateState)
            sender() ! Response(0, "Success", None)
          case None =>
            sender() ! Response(1, "Config does not exist", None)
        }

      case c:Check =>
        val orderResult = getSecurityOrderInfoFromOrder(new Date(), c.accountId)
        val orderOtherResult = getSecurityOrderOtherInfoFromOrder(new Date(), c.accountId)
        val orderInOneHour = orderOtherResult.orderNumInLastOneHour + orderResult.orderNumInLastOneHour
        val totalValidOrder = orderOtherResult.orderNumOfValidOrder + orderOtherResult.orderNumOfValidOrder
        val configMaxInHour = securityRepositoryfindByName("max_order_1_hour")
        val configMaxNotUse = securityRepositoryfindByName("max_order_not_use")
        val oneHourLine = configMaxInHour
        val totalValidLine = configMaxNotUse
        if (orderInOneHour > oneHourLine || totalValidOrder > totalValidLine) sender()  !  Response(1, "Too much order in last one hour or too much valid order", c.accountId)
        else sender() ! Response(0, "Success", c.accountId)


    }

    def getSecurityOrderInfoFromOrder(checkDate: Date, accountId: Int): OrderSecurity ={
      var orderSecurity: Option[OrderSecurity] = None
      val response: Future[Any] = orderService ? CheckSecurityAboutOrder(checkDate,accountId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) orderSecurity = Some(res.asInstanceOf[Response].data.asInstanceOf[OrderSecurity])
          else orderSecurity = None
        case Failure(_) =>
          orderSecurity = None
      }
      orderSecurity.get
    }

    def getSecurityOrderOtherInfoFromOrder(checkDate: Date, accountId: Int): OrderSecurity = {
      var orderSecurity: Option[OrderSecurity] = None
      val response: Future[Any] = orderOtherService ? CheckSecurityAboutOrder(checkDate,accountId)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) orderSecurity = Some(res.asInstanceOf[Response].data.asInstanceOf[OrderSecurity])
          else orderSecurity = None
        case Failure(_) =>
          orderSecurity = None
      }
      orderSecurity.get
    }

    def securityRepositoryfindByName(name : String): Int ={
      var result = 0
      for (config <-state.configs.values){
        if (config.name == name) result = config.value

      }
      result
    }


  }

}