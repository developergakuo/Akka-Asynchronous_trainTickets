import TSCommon.Commons._
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Success}
import java.util.Date

import TSCommon.Commons

implicit val timeout: Timeout = 2.seconds

object TSSeatService {
  case class SecurityRepository(configs: Map[Int, SecurityConfig])

  class SeatService extends PersistentActor {
    var state: SecurityRepository = SecurityRepository(Map())
    var orderService: ActorRef = null
    var orderOtherService: ActorRef = null
    var configService: ActorRef = null
    var travelService: ActorRef = null
    var travel2Service: ActorRef = null


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
        var routeResult: Option[Route] = None
        var trainTypeResult:Option[TrainType] = None
        var leftTicketInfo: Option[LeftTicketInfo] = None
        val trainNumber = c.seat.trainNumber
        if (trainNumber == 1 || trainNumber == 2) {

          val response: Future[Any] = travelService ? GetRouteById(c.seat.trainNumber)
          response onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) routeResult = Some(resp.data.asInstanceOf[Route])
            case Failure(_) =>
              routeResult = None
          }

          val response2: Future[Any] = orderService ? GetSoldTickets(c.seat)
          response2 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) leftTicketInfo = Some(resp.data.asInstanceOf[LeftTicketInfo])
            case Failure(_) =>
              leftTicketInfo = None
          }

          val response3: Future[Any] = travelService ? GetTrainTypeByTripId(c.seat.trainNumber)
          response3 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) trainTypeResult = Some(resp.data.asInstanceOf[TrainType])
            case Failure(_) =>
              trainTypeResult = None
          }
        }
        else {

          val response: Future[Any] = travel2Service ? GetRouteById(c.seat.trainNumber)
          response onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) routeResult = Some(resp.data.asInstanceOf[Route])
            case Failure(_) =>
              routeResult = None
          }

          val response2: Future[Any] = orderOtherService ? GetSoldTickets(c.seat)
          response2 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) leftTicketInfo = Some(resp.data.asInstanceOf[LeftTicketInfo])
            case Failure(_) =>
              leftTicketInfo = None
          }

          val response3: Future[Any] = travel2Service ? GetTrainTypeByTripId(c.seat.trainNumber)
          response3 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) trainTypeResult = Some(resp.data.asInstanceOf[TrainType])
            case Failure(_) =>
              trainTypeResult = None
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
          val soldTickets = lti.soldTickets
          for (soldTicket <- soldTickets) {
            val soldTicketDestStation = soldTicket.destStation
            if (stationList.indexOf(soldTicketDestStation) < stationList.indexOf(startStation)) {
              sender() ! Response(0, "Use a new seat number!", Ticket(seat,startStation,c.seat.destStation))
            }
          }
          while ( {
            isContained(soldTickets, seat)
          }) seat = rand.nextInt(range) + 1

         case None => // Do nothing
       }

      case c:GetLeftTicketOfInterval =>
        var numOfLeftTicket = 0
        var routeResult: Option[Route] = None
        var trainTypeResult:Option[TrainType] = None
        var leftTicketInfo: Option[LeftTicketInfo] = None
        val trainNumber = c.seat.trainNumber
        if (trainNumber == 1 || trainNumber == 2) {

          val response: Future[Any] = travelService ? GetRouteById(c.seat.trainNumber)
          response onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) routeResult = Some(resp.data.asInstanceOf[Route])
            case Failure(_) =>
              routeResult = None
          }

          val response2: Future[Any] = orderService ? GetSoldTickets(c.seat)
          response2 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) leftTicketInfo = Some(resp.data.asInstanceOf[LeftTicketInfo])
            case Failure(_) =>
              leftTicketInfo = None
          }

          val response3: Future[Any] = travelService ? GetTrainTypeByTripId(c.seat.trainNumber)
          response3 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) trainTypeResult = Some(resp.data.asInstanceOf[TrainType])
            case Failure(_) =>
              trainTypeResult = None
          }
        }
        else {

          val response: Future[Any] = travel2Service ? GetRouteById(c.seat.trainNumber)
          response onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) routeResult = Some(resp.data.asInstanceOf[Route])
            case Failure(_) =>
              routeResult = None
          }

          val response2: Future[Any] = orderOtherService ? GetSoldTickets(c.seat)
          response2 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) leftTicketInfo = Some(resp.data.asInstanceOf[LeftTicketInfo])
            case Failure(_) =>
              leftTicketInfo = None
          }

          val response3: Future[Any] = travel2Service ? GetTrainTypeByTripId(c.seat.trainNumber)
          response3 onComplete {
            case Success(resp: Response) =>
              if (resp.status == 0) trainTypeResult = Some(resp.data.asInstanceOf[TrainType])
            case Failure(_) =>
              trainTypeResult = None
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
      val response: Future[Any] = configService ? Query(configName)
      response onComplete {
        case Success(res) =>
          if (res.asInstanceOf[Response].status == 0) result = Some(res.asInstanceOf[Response].data.asInstanceOf[Config].value)
          else result = None
        case Failure(_) =>
          result = None
      }
      result.get
    }
  }
}
