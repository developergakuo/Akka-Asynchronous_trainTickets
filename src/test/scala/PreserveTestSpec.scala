import java.util.Date

import Services._
import TSCommon.Commons._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import scala.concurrent.duration.Duration

class  PreserveTestSpec() extends TestKit(ActorSystem("WebshopTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  "A client actor" must {
    "return increase orders  on seat and food preservation" in {
      //get all orders before a client makes a preservation.
      // The client might also order food
      adminOrderService ! GetAllOrders()
      adminFoodOrderService ! GetAllOrders()
      //now make a preservation with an already logged on client
      client ! ClientPreserve("station1", "station4", new Date(), 5,SeatClass().secondClass._1)
      //wait for three seconds
      Thread.sleep(3000)
      adminOrderService ! GetAllOrders()
      adminFoodOrderService ! GetAllOrders()
       //receive all messages
      val messages = receiveN(5,Duration(5000, "millis"))
      // a single preservation for a train should increase train orders by 1
      (messages(1).asInstanceOf[Response].data.asInstanceOf[List[Order]].size + 1) should equal( messages(4).asInstanceOf[Response].data.asInstanceOf[List[Order]].size )
      // a single preservation for a food should increase food orders by 1
      (messages(0).asInstanceOf[Response].data.asInstanceOf[List[FoodOrder]].size + 1) should equal( messages(3).asInstanceOf[Response].data.asInstanceOf[List[FoodOrder]].size )
    }
  }
}