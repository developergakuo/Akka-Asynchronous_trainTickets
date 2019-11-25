import Services.{system, _}
import TSCommon.Commons._
import InputData._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import akka.pattern.ask

import scala.concurrent.Await

class  PaymentTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return orderPaid message on paying a valid order" in {
      client ! ClientLogin("userName1", "psw1")
      client ! ClientPay(exampleOtherOrderUnpaid)
      expectMsgType[OrderPaid]
      val orderFuture = orderOtherService ? GetOrderById(exampleOtherOrderUnpaid.id)
      val paidOrder = Await.result(orderFuture,duration).asInstanceOf[Response].data.asInstanceOf[Order]
      println("Order status: "+paidOrder.status)
      client ! ClientRebook(paidOrder, trips(paidOrder.trainNumber-1))
      expectMsgType[OrderRebooked]

    }
  }
}