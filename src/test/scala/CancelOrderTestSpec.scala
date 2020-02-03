
import InputData._
import Services._
import TSCommon.Commons._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

class  CancelOrderTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }


  "A client actor" must {
    "return OrderCanceled message on canceling a valid order" in {
      //client login
      client ! ClientLogin("userName1", "psw1")
      //client cancel an already existing order "exampleOtherOrderUnpaid"
      client ! ClientCancelOrder(exampleOtherOrderUnpaid)
      expectMsgType[OrderCanceled]
      //wait for 3 seconds
      Thread.sleep(3000)
      //Get the paid order
      orderOtherService ! GetOrderById(exampleOtherOrderUnpaid.id)
     val orderResponse: ResponseFindOrderById= expectMsgType[ResponseFindOrderById]
      //the order should be canceled
      orderResponse.order.status should equal(OrderStatus().CANCEL._1)
    }
  }
}