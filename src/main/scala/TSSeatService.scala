import TSCommon.Commons._
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.util.{Failure, Random, Success}



object TSSeatService {

  case class SecurityRepository(configs: Map[Int, SecurityConfig])

  class SeatService (orderService: ActorRef , orderOtherService: ActorRef , configService: ActorRef,
                     routeService: ActorRef,trainService: ActorRef)extends PersistentActor {
    var state: SecurityRepository = SecurityRepository(Map())


    override def preStart(): Unit = {
      println("TravelService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("TravelService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "TravelService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: SecurityRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: AddNewSecurityConfig ⇒
        state = SecurityRepository(state.configs + (c.info.id -> c.info))

      case c: ModifySecurityConfig =>
        state = SecurityRepository(state.configs + (c.info.id -> c.info))
      case c: DeleteSecurityConfig =>
        state = SecurityRepository(state.configs - c.id)

    }


    override def receiveCommand: Receive = {
      case c:DistributeSeat =>
        println("***************** DistributeSeat")
        var routeResult: Option[Route] = None
        var trainTypeResult:Option[TrainType] = None
        var leftTicketInfo: Option[LeftTicketInfo] = None
        val trainNumber = c.seat.trainNumber
        if (trainNumber == 1 || trainNumber == 2) {
          val responseFuture: Future[Any] = routeService ? GetRouteById(c.seat.trainNumber)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0){
            println("***************** DistributeSeat: route Success")
            routeResult = Some(response.data.asInstanceOf[Route])
          }

          val response2Future: Future[Any] = orderService ? GetSoldTickets(c.seat)
          val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
          if (response2.status == 0) {
            println("***************** DistributeSeat: LeftTicket Success")
            leftTicketInfo = Some(response2.data.asInstanceOf[LeftTicketInfo])
          }

          val response3Future: Future[Any] = trainService ? RetrieveTrain(c.seat.trainNumber)
          val response3 = Await.result(response3Future,duration).asInstanceOf[Response]
          if (response3.status == 0) trainTypeResult = Some(response3.data.asInstanceOf[TrainType])
        }
        else {
          val responseFuture: Future[Any] = routeService ? GetRouteById(c.seat.trainNumber)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0){
            println("***************** DistributeSeat: route Success")
            routeResult = Some(response.data.asInstanceOf[Route])
          }

          val response2Future: Future[Any] = orderOtherService ? GetSoldTickets(c.seat)
          val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
          if (response2.status == 0){
            println("***************** DistributeSeat: solTickets Success")
            leftTicketInfo = Some(response2.data.asInstanceOf[LeftTicketInfo])
          }

          val response3Future: Future[Any] = trainService ? RetrieveTrain(c.seat.trainNumber)
          val response3 = Await.result(response3Future,duration).asInstanceOf[Response]
          if (response3.status == 0) {
            println("***************** DistributeSeat: trainType Success")
            trainTypeResult = Some(response3.data.asInstanceOf[TrainType])
          }

        }
        val stationList = routeResult.get.stations

        var seatTotalNum = 0
        if (c.seat.seatType == SeatClass().firstClass._1) {
          seatTotalNum = trainTypeResult.get.comfortClass
        }
        else {
          seatTotalNum = trainTypeResult.get.economyClass
        }
        val startStation = c.seat.startStation
        val rand = new Random
        val range = seatTotalNum
        var seat = rand.nextInt(range) + 1
       leftTicketInfo match {
         case Some(lti) =>
           val ticket = Ticket(seat,startStation,c.seat.destStation)
          val soldTickets = lti.soldTickets
          if  (soldTickets.contains(ticket)) {
            sender() ! Response(1,"Error: Seat Already taken", None)
          }
          else
            sender() ! Response(0,"Success: Seat preserved", ticket)

         case None => // Do nothing
           sender() ! Response(1,"Error: Fetching soldTickets", None)
       }

      case c:GetLeftTicketOfInterval =>
        println("SeatService: GetLeftTicketOfInterval: Begin")
        var numOfLeftTicket = 0
        var routeResult: Option[Route] = None
        var trainTypeResult:Option[TrainType] = None
        var leftTicketInfo: Option[LeftTicketInfo] = None
        val trainNumber = c.seat.trainNumber
        if (trainNumber == 1 || trainNumber == 2) {
          println("SeatService: GetLeftTicketOfInterval Begin: HighSpeed")
          println("SeatService: GetLeftTicketOfInterval Begin: HighSpeed")
          val responseFuture: Future[Any] = routeService ? GetRouteById(c.seat.trainNumber)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) {
            println("SeatService: GetLeftTicketOfInterval: HighSpeed: Route")
            routeResult = Some(response.data.asInstanceOf[Route])}

          val response2Future: Future[Any] = orderService ? GetSoldTickets(c.seat)
          val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
          if (response2.status == 0) {
            println("SeatService: GetLeftTicketOfInterval: HighSpeed: LeftTickets")

            leftTicketInfo = Some(response2.data.asInstanceOf[LeftTicketInfo])
          }

          val response3Future: Future[Any] = trainService ? RetrieveTrain(c.seat.trainNumber)
          val response3 = Await.result(response3Future,duration).asInstanceOf[Response]
          if (response3.status == 0) trainTypeResult = Some(response3.data.asInstanceOf[TrainType])
        }
        else {
          println("SeatService: GetLeftTicketOfInterval: LowSpeed")
          val responseFuture: Future[Any] = routeService ? GetRouteById(c.seat.trainNumber)
          val response = Await.result(responseFuture,duration).asInstanceOf[Response]
          if (response.status == 0) routeResult = Some(response.data.asInstanceOf[Route])

          val response2Future: Future[Any] = orderOtherService ? GetSoldTickets(c.seat)
          val response2 = Await.result(response2Future,duration).asInstanceOf[Response]
          if (response2.status == 0) {
            leftTicketInfo = Some(response2.data.asInstanceOf[LeftTicketInfo])
          }

          val response3Future: Future[Any] = trainService ? RetrieveTrain(c.seat.trainNumber)
          val response3 = Await.result(response3Future,duration).asInstanceOf[Response]
          if (response3.status == 0) trainTypeResult = Some(response3.data.asInstanceOf[TrainType])
        }
        val stationList = routeResult.get.stations
        var seatTotalNum = 0
        if (c.seat.seatType == SeatClass().firstClass._1) {
          seatTotalNum = trainTypeResult.get.comfortClass
        }
        else {
          seatTotalNum = trainTypeResult.get.economyClass
        }
        var solidTicketSize = 0
        leftTicketInfo match {
          case Some(lti) =>
            val startStation = c.seat.startStation
            val soldTickets = lti.soldTickets
              solidTicketSize = soldTickets.size
            for (soldTicket <- soldTickets) {
              val soldTicketDestStation = soldTicket.destStation
              if (stationList.indexOf(soldTicketDestStation) < stationList.indexOf(startStation)) {
                numOfLeftTicket += 1
              }
            }
          case None => // Do nothing

        }
        var direstPart = getDirectProportion()
        val route = routeResult.get
        if (route.stations.head.equals(c.seat.startStation) && route.stations.last.equals(c.seat.destStation)) {
          //do nothing
        }
        else direstPart = 1.0 - direstPart
        val unusedNum = (seatTotalNum * direstPart).toInt - solidTicketSize
        numOfLeftTicket += unusedNum
        println("SeatService: GetLeftTicketOfInterval: success")
        sender() ! Response(0, "Success", numOfLeftTicket)

    }

    def isContained(soldTickets: List[Ticket], seat: Int): Boolean = {
      var result = false
      for (soldTicket <- soldTickets) {
        if (soldTicket.seatNo == seat) result= true
      }
      result
    }

    private def getDirectProportion(configName: String = ""): Double= {
      val configName = "DirectTicketAllocationProportion"
      var result: Option[Double] = None
      val responseFuture: Future[Any] = configService ? Query(configName)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) result = Some(response.data.asInstanceOf[Config].value)
      result.get
    }
  }
}
