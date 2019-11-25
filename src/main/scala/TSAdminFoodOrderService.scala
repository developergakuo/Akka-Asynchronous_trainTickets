import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.Await
object TSAdminFoodOrderService {
 class AdminFoodOrderService (foodOrderService: ActorRef) extends Actor {

    override def receive: Receive = {
      case c:GetAllOrders =>
        println("Sending orders")
        var orders1: List[FoodOrder] = List()
        var orderResponse: Option[List[FoodOrder]] = None
        val responseFuture: Future[Any] = foodOrderService ? c
        val response = Await.result(responseFuture,duration).asInstanceOf[Response]
        if (response.status == 0) orderResponse = Some(response.data.asInstanceOf[List[FoodOrder]])
        orders1 = orderResponse.get
        println("Sending orders")
        sender() ! Response(0, "Success", orders1 )
    }
 }

}



