import TSCommon.Commons._
import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import akka.pattern.ask




object TSTicketInfoService {

  class TicketInfoService(basicService: ActorRef) extends Actor {

    override def receive: Receive = {
      //request for goods from stockActor
      case c:QueryForTravel =>
        println("======== TicketInfoService: Querry for travel: ")
        val responseFuture: Future[Any] = basicService ? c
        val response = Await.result(responseFuture,duration).asInstanceOf[Response]
        if(response.status == 0) {
          println("======== TicketInfoService: Querry for travel: Success")
          sender() ! Response(0,"Success",response.data.asInstanceOf[TravelResult])
        }
        else sender() ! Response(1,"failed",response.data)
      case c:QueryForStationId =>
        val responseFuture: Future[Any] = basicService ? c
        val response = Await.result(responseFuture,duration).asInstanceOf[Response]
        if(response.status == 0){
          println("======== TicketInfoService: QueryForStationId: Success ")
          sender() ! Response(0,"Success",response.data.asInstanceOf[Int])
        }
        else sender() ! Response(1,"failed",response.data)
    }

  }


}
