import Services.{system, _}
import TSCommon.Commons._
import InputData._
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import java.util.Date
import akka.pattern.ask

import scala.concurrent.Await

class  PreserveTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return preservedSucess  on seat preservation" in {
      client ! ClientPreserve("station1", "station4", new Date(), 5,SeatClass().secondClass._1)
      adminOrderService ! GetAllOrders()
      adminFoodOrderService ! GetAllOrders()
      receiveN(3)
    }
  }
}