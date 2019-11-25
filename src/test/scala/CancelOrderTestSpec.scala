
import Services.{system, _}
import TSCommon.Commons._
import InputData._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import akka.pattern.ask

import scala.concurrent.Await

class  CancelOrderTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return OrderCanceled message on canceling a valid order" in {
      client ! ClientLogin("userName1", "psw1")
      client ! ClientCancelOrder(exampleOtherOrderUnpaid)
      expectMsgType[OrderCanceled]
    }
  }
}