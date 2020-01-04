
import Services.{system, _}
import TSCommon.Commons._
import InputData._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import akka.pattern.ask

import scala.concurrent.Await

class  PaymentTest2Spec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return orderPaid message on paying a valid order" in {
      client ! ClientLogin("userName1", "psw1")
      client ! AddMoney(0,1,35)
      client ! ClientPay(exampleOtherOrderUnpaid)
      val messages = receiveN(2)
      println(messages(1) +"=======*")
      messages(1).getClass.toString should include ("OrderPaid")
      val orderFuture = orderOtherService ? GetOrderById(exampleOtherOrderUnpaid.id)
      val paidOrder = Await.result(orderFuture,duration).asInstanceOf[ResponseFindOrderById].order
      println("Order status: "+paidOrder.status)
      client ! ClientRebook(paidOrder, trips(paidOrder.trainNumber-1))
      expectMsgType[OrderRebooked]

    }
  }
}