 import TSCommon.Commons.{Response, _}
 import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
 import akka.util.Timeout
 import scala.concurrent.duration._

 object TSAssuranceService {

   case class AssuranceRepository(assurances: Map[Int, Assurance])

   class AssuranceService extends PersistentActor {
     var state: AssuranceRepository = AssuranceRepository(Map())

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
       case SnapshotOffer(_, offeredSnapshot: AssuranceRepository) ⇒ state = offeredSnapshot
       case RecoveryCompleted =>
         println("TravelService RecoveryCompleted")
       case x: Evt ⇒
         println("recovering: " + x)
         updateState(x)
     }

     def updateState(evt: Evt): Unit = evt match {
       case c: CreateAssurance2 ⇒
         val id = state.assurances.size
         state = AssuranceRepository(state.assurances + (id -> Assurance(id, c.orderId, c.assuranceType)))
       case c: DeleteById =>
         state = AssuranceRepository(state.assurances - c.assuranceId)
     }

     override def receiveCommand: Receive = {
       case c: FindAssuranceById =>
         state.assurances.get(c.id) match {
           case Some(assurance) =>
             sender() ! Response(0, "Success", assurance)
           case None =>
             sender() ! Response(1, "Error", None)
         }

       case c: FindAssuranceByOrderId =>
         val assurance = state.assurances.values.filter(a => a.orderId == c.orderId).toList
         if (assurance.nonEmpty) sender() ! Response(0, "Success", assurance.head)
         else sender() ! Response(1, "Error: No assurance found", None)

       case c: CreateAssurance =>
         val at = AssuranceType().assuranceTypes.filter(at => at._1 == c.typeIndex)
         val assurance = state.assurances.values.filter(a => a.orderId == c.orderId).toList
         if (assurance.nonEmpty) sender() ! Response(1, "Assurance already exists", None)
         else if (at.isEmpty) sender() ! Response(1, "Assurance type does not exists", None)
         else {
           persist(CreateAssurance2(c.orderId, at.head))(updateState)
           sender() ! Response(0, "Success", None)
         }

       case c: DeleteById =>
         state.assurances.get(c.assuranceId) match {
           case Some(assurance) =>
             persist(c)(updateState)
             sender() ! Response(0, "Success", assurance)
           case None =>
             sender() ! Response(1, "Error: Assurance does not exist", None)
         }

       case c: DeleteByOrderId =>
         val assurance = state.assurances.values.filter(a => a.orderId == c.orderId).toList
         if (assurance.nonEmpty) {
           persist(DeleteById(assurance.head.id))(updateState)
           sender() ! Response(0, "Success", assurance.head)
         } else sender() ! Response(1, "Error: No assurance found", None)

       case c: Modify =>
         val at = AssuranceType().assuranceTypes.filter(at => at._1 == c.typeIndex)
         val assurance = state.assurances.values.filter(a => a.orderId == c.orderId).toList
         if (assurance.isEmpty) sender() ! Response(1, "Assurance does not exists", None)
         else if (at.nonEmpty) {
           persist(CreateAssurance2(c.orderId, at.head))(updateState)
           sender() ! Response(0, "Success", None)
         }
         else sender() ! Response(1, "Assurance type does not exists", None)

       case GetAllAssurances =>
         sender() ! Response(0, "Success", state.assurances.values.toList)

       case GetAllAssuranceTypes =>
         val assuarnceTyoes: List[(Int, String, Double)] = state.assurances.values.map(a => a.assurance).toList
         sender() ! Response(0, "Success", assuarnceTyoes)

     }


   }

 }






