import TSAdminBasicInfoService.AdminBasicInfoService
import TSAdminFoodOrderService.AdminFoodOrderService
import TSAdminOrderService.AdminOrderService
import TSAdminRouteService.AdminRouteService
import TSAdminTravelService.AdminTravelService
import TSAdminUserService.AdminUserService
import TSAssuranceService.AssuranceService
import TSAuthService.AuthService
import TSBasicService.BasicService
import TSCancelService.CancelService
import TSConfigService.ConfigService
import TSConsignPriceService.ConsignPriceService
import TSConsignService.ConsignService
import TSContactService.ContactService
import TSFoodMapService.FoodMapService
import TSFoodService.FoodService
import TSInsidePaymentService.InsidePaymentService
import TSNotificationService.NotificationService
import TSOrderOtherService.OrderOtherService
import TSOrderService.OrderService
import TSPaymentService.PaymentService
import TSPreserveOtherService.PreserveOtherService
import TSPreserveService.PreserveService
import TSPriceService.PriceService
import TSRebookService.RebookService
import TSRoutePlanService.RoutePlanService
import TSRouteService.RouteService
import TSSeatService.SeatService
import TSSecurityService.SecurityService
import TSStationService.StationService
import TSTicketInfoService.TicketInfoService
import TSTrainService.TrainService
import TSTravel2Service.Travel2Service
import TSTravelPlanService.TravelPlanService
import TSTravelService.TravelService
import TSUserService.UserService
import akka.actor.{ActorRef, ActorSystem, Props}
object Services  {
  val system = ActorSystem("TrainTicket")
  //independent services
  val trainService : ActorRef =  system.actorOf(Props(classOf[TrainService]), "trainService")
  val stationService : ActorRef = system.actorOf(Props(classOf[StationService]), "stationService")
  val routeService : ActorRef = system.actorOf(Props(classOf[RouteService]), "routeService")
  val priceService : ActorRef = system.actorOf(Props(classOf[PriceService]), "priceService")
  val paymentService : ActorRef =system.actorOf(Props(classOf[PaymentService]), "paymentService")
  val notificationService : ActorRef = system.actorOf(Props(classOf[NotificationService]), "notificationService")
  val foodMapService : ActorRef =  system.actorOf(Props(classOf[FoodMapService]), "foodMapService")
  val contactService : ActorRef = system.actorOf(Props(classOf[ContactService]), "contactService")
  val consignPriceService : ActorRef = system.actorOf(Props(classOf[ConsignPriceService]), "consignPriceService")
  val configService : ActorRef = system.actorOf(Props(classOf[ConfigService]), "configService")
  val authService : ActorRef = system.actorOf(Props(classOf[AuthService]), "authService")
  val assuranceService : ActorRef = system.actorOf(Props(classOf[AssuranceService]), "assuranceService")
  //dependent services
  val basicService : ActorRef = system.actorOf(Props(classOf[BasicService],stationService,trainService,routeService,priceService), "basicService")
  val consignService  : ActorRef = system.actorOf(Props(classOf[ConsignService],consignPriceService), "consignService")
  val orderService : ActorRef = system.actorOf(Props(classOf[OrderService],stationService), "orderService")
  val orderOtherService : ActorRef = system.actorOf(Props(classOf[OrderOtherService],stationService), "orderOtherService")
  val ticketInfoService : ActorRef =  system.actorOf(Props(classOf[TicketInfoService],basicService), "ticketInfoService")
  val userService : ActorRef = system.actorOf(Props(classOf[UserService],authService), "userService")
  val seatService : ActorRef = system.actorOf(Props(classOf[SeatService],orderService,orderOtherService,configService,routeService,trainService), "seatService")
  val travel2Service : ActorRef = system.actorOf(Props(classOf[Travel2Service],routeService , trainService , ticketInfoService ,
    orderOtherService,seatService ), "travel2service")
  val travelService : ActorRef = system.actorOf(Props(classOf[TravelService],routeService,trainService,ticketInfoService,
    orderOtherService,seatService), "travelService")
  val routePlanService : ActorRef =  system.actorOf(Props(classOf[RoutePlanService],orderService,orderOtherService,configService,
    travelService,travel2Service,stationService,routeService), "routePlanService")
  val travelPlanService : ActorRef = system.actorOf(Props(classOf[TravelPlanService],authService , ticketInfoService , stationService ,
    travel2Service , travelService , seatService , routePlanService), "travelPlanService")
  val  securityService : ActorRef = system.actorOf(Props(classOf[SecurityService],orderService,orderOtherService), "securityService")
  val insidePaymentService : ActorRef = system.actorOf(Props(classOf[InsidePaymentService],paymentService,orderService,
    orderOtherService,notificationService), "insidePaymentService")
  val foodService : ActorRef = system.actorOf(Props(classOf[FoodService],foodMapService,travelService,stationService), "foodService")
  val rebookService : ActorRef = system.actorOf(Props(classOf[RebookService],orderService,orderOtherService,travelService,travel2Service,
    stationService,insidePaymentService,seatService,notificationService), "rebookService")
  val  preserveService : ActorRef =  system.actorOf(Props(classOf[PreserveService],ticketInfoService,  securityService ,
    contactService , travelService , stationService , seatService,orderService ,assuranceService,foodService,consignService,userService,
    notificationService), "preserveService")
  val preserveOtherService : ActorRef =system.actorOf(Props(classOf[PreserveOtherService],ticketInfoService,  securityService ,
    contactService , travel2Service , stationService , seatService,orderOtherService,assuranceService,foodService,consignService,userService,
    notificationService), "preserveOtherService")
  val cancelService: ActorRef = system.actorOf(Props(classOf[CancelService],orderService,orderOtherService,travelService,travel2Service,
    stationService,insidePaymentService,seatService,userService,notificationService), "cancelService")
  //admin service
  val adminUserService : ActorRef =system.actorOf(Props(classOf[AdminUserService],userService), "adminUserService")
  val AdminTravelService : ActorRef = system.actorOf(Props(classOf[AdminTravelService],travelService,travel2Service), "AdminTravelService")
  val adminRouteService : ActorRef = system.actorOf(Props(classOf[AdminRouteService],routeService), "adminRouteService")
  val adminOrderService : ActorRef = system.actorOf(Props(classOf[AdminOrderService],orderService,orderOtherService), "adminOrderService")
  val adminBasicInfoService : ActorRef = system.actorOf(Props(classOf[AdminBasicInfoService],routeService,trainService,ticketInfoService,
    orderService, seatService,contactService,stationService,configService,priceService), "adminBasicInfoService")
  val client : ActorRef = system.actorOf(Props(classOf[TSClient.Client],userService,travelPlanService, preserveService, preserveOtherService,
    contactService,stationService, foodMapService, insidePaymentService, rebookService,cancelService, orderService, orderOtherService),"client")
  val adminFoodOrderService: ActorRef = system.actorOf(Props(classOf[AdminFoodOrderService],foodService: ActorRef),  "adminFoodOrderService")
}
