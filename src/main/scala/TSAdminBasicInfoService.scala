
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


object TSAdminBasicInfoService {


  class AdminBasicInfoService(routeService: ActorRef , trainService: ActorRef , trainTicketInfoService: ActorRef ,
                              orderService: ActorRef , seatService: ActorRef , contactService: ActorRef ,
                              stationService: ActorRef , configService: ActorRef , priceService: ActorRef) extends Actor {
    override def receive: Receive = {

      ////////////////////////////contactservice///////////////////////////////
      case GetAllContacts =>
        var contacts: Option[List[Contacts]] = None
        val response: Future[Any] = contactService ? GetAllContacts()
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) contacts = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Contacts]])
            else contacts = None
          case Failure(_) =>
            contacts = None
        }
        contacts match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
           case None =>
             sender() ! Response(1,"Error",None)
        }


      case c:AddContact =>
        val response: Future[Any] = contactService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }


      case c:DeleteContact =>
        val response: Future[Any] = contactService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:ModifyContact =>
        val response: Future[Any] = contactService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }


      ////////////////////////////station///////////////////////////////
      case GetAllStations =>
        var stations: Option[List[Station]] = None
        val response: Future[Any] = stationService ? QueryStations
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) stations = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Station]])
            else stations = None
          case Failure(_) =>
            stations = None
        }
        stations match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(1,"Error",None)
        }

      case c: AddStation =>
        val response: Future[Any] = contactService ? CreateStation(c.s)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:DeleteStation =>
        val response: Future[Any] = contactService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:ModifyStation =>
        val response: Future[Any] = contactService ? UpdateStation(c.s)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      ////////////////////////////train///////////////////////////////
      case GetAllTrains =>
        var Trains: Option[List[TrainType]] = None
        val response: Future[Any] = trainService ? QueryTrains()
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) Trains = Some(res.asInstanceOf[Response].data.asInstanceOf[List[TrainType]])
            else Trains = None
          case Failure(_) =>
            Trains = None
        }
        Trains match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(0,"Error",None)
        }

      case c:AddTrain =>
        val response: Future[Any] = trainService ? CreateTrain(c.t)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:DeleteTrain =>
        val response: Future[Any] = trainService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:ModifyTrain =>
        val response: Future[Any] = trainService ? UpdateTrain(c.t)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      ////////////////////////////config///////////////////////////////
      case c:GetAllConfigs =>
        var configs: Option[List[Config]] = None
        val response: Future[Any] = configService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) configs = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Config]])
            else configs = None
          case Failure(_) =>
            configs = None
        }
        configs match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(0,"Error",None)
        }

      case c:AddConfig =>
        val response: Future[Any] = configService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:DeleteConfig =>
        val response: Future[Any] = configService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c: ModifyConfig =>
        val response: Future[Any] = configService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      ////////////////////////////price///////////////////////////////
      case GetAllPrices =>
        var prices: Option[List[PriceConfig]] = None
        val response: Future[Any] = priceService ? FindAllPriceConfig()
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) prices = Some(res.asInstanceOf[Response].data.asInstanceOf[List[PriceConfig]])
            else prices = None
          case Failure(_) =>
            prices = None
        }
        prices match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(0,"Error",None)
        }

      case c:AddPrice =>
        val response: Future[Any] = configService ? CreateNewPriceConfig(PriceConfig(c.pi.id,c.pi.trainType,
          c.pi.routeId,c.pi.basicPriceRate,c.pi.firstClassPriceRate))
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }


      case c: DeletePrice =>
        val response: Future[Any] = configService ? DeletePriceConfig(PriceConfig(c.pi.id,c.pi.trainType,
          c.pi.routeId,c.pi.basicPriceRate,c.pi.firstClassPriceRate))
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

      case c:ModifyPrice =>
        val response: Future[Any] = configService ? UpdatePriceConfig(PriceConfig(c.pi.id,c.pi.trainType,
          c.pi.routeId,c.pi.basicPriceRate,c.pi.firstClassPriceRate))
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }

    }
  }

}



