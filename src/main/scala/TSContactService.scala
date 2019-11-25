import TSCommon.Commons.{Response, _}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import InputData._

object TSContactService {
  case class ContactRepository(contacts: Map[Int, Contacts])

  class ContactService extends PersistentActor {
    var state: ContactRepository = ContactRepository(contacts.zipWithIndex.map(a=>a._2 -> a._1).toMap)
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
      case SnapshotOffer(_, offeredSnapshot: ContactRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("TravelService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: AddContact ⇒ state = ContactRepository(state.contacts + (c.contact.id -> c.contact))
      case c: DeleteContact => state = ContactRepository(state.contacts - c.contactsId)
    }

    override def receiveCommand: Receive = {
      case c:AddContact =>
        state.contacts.get(c.contact.id) match {
          case Some(_) =>
            sender() ! Response(1, "Error: Already exists", None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0, "Added", None)
        }

      case c:DeleteContact =>
        state.contacts.get(c.contactsId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0, "Deleted", None)

          case None =>
            sender() ! Response(1, "Error: contact does not exists", None)
        }

      case c:ModifyContact =>
        state.contacts.get(c.mci.id) match {
          case Some(_) =>
            persist(AddContact(c.mci))(updateState)
            sender() ! Response(0, "Updated", None)

          case None =>
            sender() ! Response(1, "Error: contact does not exists", None)
        }

      case GetAllContacts =>
        sender() ! Response(0, "Success", state.contacts.values.toList)

      case c:FindContactsById =>
        state.contacts.get(c.id) match {
          case Some(contcts) =>
            sender() ! Response(0, "Found", contcts)

          case None =>
            sender() ! Response(1, "Error: contact does not exists", None)
        }

      case c:FindContactsByAccountId =>
        val contact: Iterable[Contacts] = state.contacts.values.filter(contacts => contacts.accountId == c.accountId)
        if(contact.nonEmpty){
          sender() ! Response(0, "Found", contact.head)

        }else{
          sender() ! Response(1, "Not Found", None)
        }

    }

  }
}