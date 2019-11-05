import TSCommon.Commons._
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}

object TSStationService {
  case class StationRepository(stations:Map[Int,Station])
  class StationService extends PersistentActor {
    var state = StationRepository(stations = Map())
    override def preStart(): Unit = {
      println("StationService prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("StationService post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "StationService-Client-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: StationRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("Client RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: CreateStation ⇒
        state = StationRepository(state.stations + (c.station.id -> c.station))
      case c: UpdateStation ⇒
        state = StationRepository(state.stations + (c.station.id -> c.station))
      case c: DeleteStation ⇒
        state = StationRepository(state.stations - c.station.id)

    }


    override def receiveCommand: Receive = {
      case c:CreateStation =>
        state.stations.get(c.station.id) match {
          case Some(_) =>
            sender() ! Response(1,"Station with a similar Id already exists",None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.station.id)
        }
      case c: ExistStation =>
        var exists = false
        for(station<- state.stations){
          if (station._2.name == c.stationName) exists = true
        }
        if (exists) sender() ! Response(0,"Success",data = true)
        else sender() ! Response(1,"Failure",data = false)
      case c:UpdateStation =>
        state.stations.get(c.station.id) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.station.id)
          case None =>
            sender() ! Response(1,"Station does not exist",None)
        }
      case c:DeleteStation =>
        state.stations.get(c.station.id) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.station.id)
          case None =>
            sender() ! Response(1,"Station does not exist",None)
        }
      case QueryStations =>
        sender() ! Response(0,"Success",state.stations.values.toList)

      case c:QueryForIdStation =>
        var station: Option[Station] = None
        for(stn<- state.stations.values){
          if (stn.name == c.stationName) station = Some(stn)
        }

      case c:QueryForIdBatchStation =>
        var result: List[Int] = List()
        for(stationName <- c.nameList){
          var station: Option[Station] = None
          for(stn<- state.stations.values){
            if (stn.name == stationName) station = Some(stn)
          }
          station match {
            case Some(stn) => result= stn.id :: result
            case None => result = -1 :: result
          }
        }
        if (result.nonEmpty) sender() ! Response(0, "Success", result.reverse)
        else sender() ! Response(1, "No content according to name list", c.nameList)
      case c:QueryByIdStation =>
        state.stations.get(c.stationId) match {
          case Some(station) =>
            sender() ! Response(0,"Success",station.name)
          case None =>
            sender() ! Response(1,"Failure",None)
        }
      case c:QueryByIdBatchStation =>
       val stationNames: List[String] = c.stationIdList.map(a => state.stations.get(a)).filter(a=>a match {
          case Some(_) => true
          case None => false
        }).map(a=>a.get.name)
       if(stationNames.nonEmpty) sender() ! Response(0, "Success", stationNames)
       else sender() ! Response(1, "Failure", stationNames)
    }

  }

}
