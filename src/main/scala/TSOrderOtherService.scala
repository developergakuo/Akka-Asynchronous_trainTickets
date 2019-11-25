import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.Await
import java.util.Calendar
import InputData.{exampleOtherOrder,exampleOtherOrderUnpaid}
import scala.collection.mutable.ListBuffer



object TSOrderOtherService {
  case class orderRepository(orders: Map[Int, Order])
  class OrderOtherService( stationService: ActorRef) extends PersistentActor {
    var state: orderRepository = orderRepository(Map(1->exampleOtherOrder,2->exampleOtherOrderUnpaid))
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
      case SnapshotOffer(_, offeredSnapshot: orderRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: Create ⇒
        state = orderRepository(state.orders + (c.newOrder.id -> c.newOrder))
      case c: DeleteOrder =>
        state = orderRepository(state.orders - c.orderId)

    }

    override def receiveCommand: Receive = {
      case c: FindOrderById =>
        state.orders.get(c.id) match {
          case Some(order) =>
            sender() ! Response(0, "Success", order)
          case None =>
            sender() ! Response(1, "No order with that id", None)
        }

      case c: Create =>
        state.orders.get(c.newOrder.id) match {
          case Some(_) =>
            sender() ! Response(1, "Order with that id exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", c.newOrder)
        }
      case c:SaveChanges =>
        state.orders.get(c.order.id) match {
          case Some(order) =>
            sender() ! Response(1, "Order with similar id does not exist", order)
          case None =>
            persist(Create(c.order))(updateState)

            sender() ! Response(0, "Success: Order Changed", None)
        }
      case c:CancelOrder =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            order.status = OrderStatus().CANCEL._1
            persist(Create(order))(updateState)
            sender() ! Response(0, "Success: order canceled", None)
          case None =>
            sender() ! Response(1, "order not found", None)
        }

      case c:QueryOrders =>
        val orders = queryOrders(c.qi,c.accountId)
        if(orders.nonEmpty) sender() ! Response(0,"Success: orders", orders)
        else sender() ! Response(1, "Empty orders", None)


      case c:QueryOrdersForRefresh =>
        val orders =   queryOrders(c.qi, c.accountId)
        var stationIds: List[Int] = List()
        for ( order <- orders) {
          stationIds = order.from :: stationIds
          stationIds = order.to :: stationIds
        }
        //val names = queryForStationId(stationIds.reverse)
        var i =0
        for (order<-orders) {
          // order.from = (names(i * 2))
          //order.to =  (names(i * 2 + 1))
          i = 1+1
        }
        sender() !  Response(1, "Query Orders For Refresh Success", orders)
      case c: AlterOrder =>
        state.orders.get(c.oai.previousOrderId) match {
          case Some(order) =>
            val tempOrder = order
            tempOrder.status = OrderStatus().CANCEL._1
            saveChanges(tempOrder)
            val newOrder = c.oai.newOrderInfo
            newOrder.id = scala.util.Random.nextInt(10000000)
            state.orders.get(newOrder.id) match {
              case Some(_) =>
                sender() ! Response(1, "Alter Order: Order with that id exists", None)
              case None =>
                persist(c)(updateState)
                Response(0, "Success", None)
            }
          case None =>
            sender() ! Response(1, "Old order does not exist", None)
        }
      case c:GetSoldTickets =>
          val list = state.orders.values.filter(ord => ord.travelDate == c.seat.travelDate)
          if (list.nonEmpty) {
            val ticketSet: ListBuffer[Ticket] = ListBuffer()
            for (tempOrder <- list) {
              ticketSet.+=( Ticket(tempOrder.seatNumber, tempOrder.from, tempOrder.to))
            }
            val leftTicketInfo =LeftTicketInfo(ticketSet.toList)
            sender() ! Response(0, "Success", leftTicketInfo)
          }
          else {
            System.out.println("Left ticket info is empty")
            sender() ! Response(0, "Order is Null.", LeftTicketInfo(List()))
          }

      case c: QueryAlreadySoldOrders =>
        val cstr = SoldTicket(travelDate = c.travelDate,trainNumber = c.trainNumber)
        for (order <- state.orders.values) {
          if ((order.trainNumber == c.trainNumber) && (order.travelDate == c.travelDate)) {
            if (order.status >= OrderStatus().CHANGE._1)
              if (order.seatClass == SeatClass().none._1) cstr.noSeat = cstr.noSeat + 1
              else if (order.seatClass == SeatClass().business._1) cstr.businessSeat=cstr.businessSeat + 1
              else if (order.seatClass == SeatClass().firstClass._1) cstr.firstClassSeat =cstr.firstClassSeat + 1
              else if (order.seatClass == SeatClass().secondClass._1) cstr.secondClassSeat = cstr.secondClassSeat + 1
              else if (order.seatClass == SeatClass().hardSeat._1) cstr.hardSeat =cstr.hardSeat + 1
              else if (order.seatClass == SeatClass().softSeat._1) cstr.softSeat = cstr.softSeat + 1
              else if (order.seatClass == SeatClass().hardBed._1) cstr.hardBed =cstr.hardBed + 1
              else if (order.seatClass == SeatClass().softBed._1) cstr.softBed =cstr.softBed + 1
              else if (order.seatClass == SeatClass().highSoftBed._1) cstr.highSoftBed = cstr.highSoftBed + 1
              else System.out.println("[Order Service][Calculate Sold Tickets] Seat class not exists. Order ID:" + order.id)
          }

        }
        sender() ! Response(0, "Success: Sold Tickets", cstr)

      case c:GetAllOrders =>
        sender() ! Response(0, "Success", state.orders.values.toList)

      case c: ModifyOrder =>
        state.orders.get(c.orderId) match {
          case Some(order) =>

            order.status = c.status
            saveChanges(order)
            println("Success: order modified")
            sender() ! Response(0, "Success: order modified", None)
          case None =>
            sender() ! Response(1, "Failure: order not found", None)

        }

      case c: GetOrderPrice =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            sender() ! Response(0, "Success: Price", order.price)
          case None =>
            sender() ! Response(1, "Failure: order Not found", None)
        }

      case c: PayOrder =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            order.status = OrderStatus().PAID._1
            saveChanges(order)
            sender() ! Response(0, "Success: Paid", order.price)
          case None =>
            sender() ! Response(1, "Failure: Not Found", None)
        }

      case c: GetOrderById =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            println("======== orderOtherService: GetOrderByIdSuccess " )
            sender() ! Response(0, "Success", order)
          case None =>
            println("======== orderOtherService: GetOrderByIdFailure " )
            sender() ! Response(1, "No order with that id", None)
        }
      case c:CheckSecurityAboutOrder =>
        val orders = state.orders.values.filter(order => order.accountId == c.accountId)
        var countOrderInOneHour = 0
        var countTotalValidOrder = 0
        val ca: Calendar = Calendar.getInstance
        ca.setTime(c.dateFrom)
        ca.add(Calendar.HOUR_OF_DAY, -1)
        val dateFrom = ca.getTime
        for (order <- orders) {
          if ((order.status == OrderStatus().NOTPAID._1) || (order.status == OrderStatus().PAID._1) ||
            (order.status == OrderStatus().COLLECTED._1)) countTotalValidOrder += 1
          if (order.boughtDate.after(dateFrom)) countOrderInOneHour += 1
        }
        val result = OrderSecurity(countOrderInOneHour,countTotalValidOrder)
        sender() !  Response(0, "Check Security Success . ", result)



      case c:InitOrder =>
        state.orders.get(c.order.id) match {
          case Some(_) =>
            sender() ! Response(1, "Order with that id exists", None)
          case None =>
            persist(c)(updateState)
            Response(0, "Success", None)
        }


      case c:DeleteOrder =>
        state.orders.get(c.orderId) match {
          case Some(order) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success: Delete", order)
          case None =>
            sender() ! Response(1, "No order with that id", None)
        }
      case c:UpdateOrder =>
        state.orders.get(c.order.id) match {
          case Some(order) =>
            saveChanges(c.order)
            sender() ! Response(0, "Success: Delete", order)
          case None =>
            sender() ! Response(1, "No order with that id", None)
        }
    }

    def saveChanges(order: Order): Unit = {
      persist(Create(order))(updateState)

    }
    def queryOrders(qi:OrderInfo, accountId: Int):List[Order] = { //1.Get all orders of the user
      val list = state.orders.values.filter(order =>order.accountId ==accountId)
      var finalList: List[Order] = List()
      //Check is these orders fit the requirement/
      if (qi.enableStateQuery || qi.enableBoughtDateQuery || qi.enableTravelDateQuery) {
        for (tempOrder <- list) {
          var statePassFlag = false
          var boughtDatePassFlag = false
          var travelDatePassFlag = false
          //3.Check order state requirement.
          if (qi.enableStateQuery) if (tempOrder.status != qi.state) statePassFlag = false
          else statePassFlag = true
          else statePassFlag = true
          System.out.println("[Order Service][Query Order][Step 2][Check Status Fits End]")
          //4.Check order travel date requirement.
          if (qi.enableTravelDateQuery) if (tempOrder.travelDate.before(qi.travelDateEnd) && tempOrder.travelDate.after(qi.boughtDateStart)) travelDatePassFlag = true
          else travelDatePassFlag = false
          else travelDatePassFlag = true
          System.out.println("[Order Service][Query Order][Step 2][Check Travel Date End]")
          //5.Check order bought date requirement.
          if (qi.enableBoughtDateQuery) if (tempOrder.boughtDate.before(qi.boughtDateEnd) && tempOrder.boughtDate.after(qi.boughtDateStart)) boughtDatePassFlag = true
          else boughtDatePassFlag = false
          else boughtDatePassFlag = true
          System.out.println("[Order Service][Query Order][Step 2][Check Bought Date End]")
          //6.check if all requirement fits.
          if (statePassFlag && boughtDatePassFlag && travelDatePassFlag) finalList = tempOrder :: finalList
          System.out.println("[Order Service][Query Order][Step 2][Check All Requirement End]")
        }
        System.out.println("[Order Service][Query Order] Get order num:" + finalList.size)
      }
      finalList
    }
    def queryForStationId(queryIds: List[Int]):List[String]  = {
      var names: Option[List[String]] = None
      val responseFuture: Future[Any] = stationService ? QueryByIdBatchStation(queryIds)
      val response = Await.result(responseFuture,duration).asInstanceOf[Response]
      if (response.status == 0) names = Some(response.data.asInstanceOf[List[String]])
      names.get
    }
  }

}





