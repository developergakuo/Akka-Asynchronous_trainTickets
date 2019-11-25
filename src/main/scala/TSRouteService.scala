import TSCommon.Commons._
import akka.actor. ActorSelection
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import InputData._

object TSRouteService {

  case class RouteRepository(routes: Map[Int, Route])
  class RouteService extends PersistentActor {
    var state: RouteRepository = RouteRepository(routes.zipWithIndex.map(a => a._2+1 ->a._1).toMap)
    override def preStart(): Unit = {
      println("RouteRepository prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("RouteRepository post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "RouteRepository-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: RouteRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")

      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)

    }

    def updateState(evt: Evt): Unit = evt match {
      case c: DeleteRoute ⇒
        state = RouteRepository(state.routes - c.routeId)
      case c: CreateAndModify =>
        state =
          RouteRepository(state.routes + (c.routeInfo.routeId ->
            Route(c.routeInfo.routeId,
              c.routeInfo.stationList,
              c.routeInfo.distanceList,
              c.routeInfo.startStation,
              c.routeInfo.endStation)))
    }

    override def receiveCommand: Receive = {
      case c: GetRouteByStartAndTerminal =>
        val routes: List[Route] =
          state.routes
            .filter(a => a._2.stations.contains(c.startId) && a._2.stations.contains(c.terminalId)).values.toList
        if (routes.isEmpty) sender() ! Response(1, "No matching Routes", None)
        else sender() ! Response(0, "Success", routes)

      case GetAllRoutes =>
        sender() ! Response(0, "Success", state.routes)

      case c: GetRouteById =>
        state.routes.get(c.routeId) match {
          case Some(route) =>
            println("Get RouteByID: Success")
            sender() ! Response(0, "Success", route)
          case None =>
            sender() ! Response(1, "No Route with matching id", None)
        }

      case c: DeleteRoute =>
        state.routes.get(c.routeId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Success", c.routeId)
          case None =>
            sender() ! Response(1, "No Route with matching id", None)
        }

      case c: CreateAndModify =>
        persist(c)(updateState)
        sender() ! Response(0, "Success", None)

    }
  }

}
