
import InputData._
import Services._
import TSCommon.Commons._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

class  RebookTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return OrderRebooked message on rebooking a paid order" in {
      //client login
      //client ! ClientLogin("userName1", "psw1")
      //client obtain an already paid order and choose to a different train/trip
      client ! ClientRebook(exampleOtherOrderPaid, trips(exampleOtherOrderPaid.trainNumber-1))
      expectMsgType[OrderRebooked]
      //wait for three seconds
      Thread.sleep(3000)
      //get the order
      orderOtherService ! GetOrderById(exampleOtherOrderPaid.id)
      //the order should have a changed  status
      val orderResponse: ResponseFindOrderById= expectMsgType[ResponseFindOrderById]
      orderResponse.order.status should equal(OrderStatus().CHANGE._1)
    }
  }
}