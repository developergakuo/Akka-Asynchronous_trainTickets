import InputData._
import Services._
import TSCommon.Commons._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import scala.concurrent.Await

class  PaymentTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return orderPaid message on paying a valid order" in {
      //client login
      client ! ClientLogin("userName2", "psw2")
      //client pays for an already preserved but unpaid order
      client ! ClientPay(exampleOtherOrderUnpaid)
      expectMsgType[OrderPaid]
      //get  the order
      val orderFuture = orderOtherService ? GetOrderById(exampleOtherOrderUnpaid.id)
      val paidOrder = Await.result(orderFuture,duration).asInstanceOf[ResponseFindOrderById].order
      //The order should be paid
      paidOrder.status should equal(OrderStatus().PAID._1)
    }
  }
}