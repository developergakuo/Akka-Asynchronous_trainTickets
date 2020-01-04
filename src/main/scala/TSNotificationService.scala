
import TSCommon.Commons.{Response, _}
import akka.actor. ActorRef
import akka.persistence._

import scala.collection.mutable.ListBuffer


object TSNotificationService {
case class EmailRepository(outbox: Map[ActorRef,ListBuffer[Mail]])
class NotificationService extends PersistentActor with AtLeastOnceDelivery {
  var state = EmailRepository(Map())
  override def preStart(): Unit = {
    println("UserService prestart")
    super.preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    println("UserService post restart")
    println(reason)
    super.postRestart(reason)
  }

  override def persistenceId = "NotificationService-id"

  override def recovery: Recovery = super.recovery

  override def receiveRecover: Receive = {
    case e:SaveMail  ⇒


  }

  def updateState(evt: Evt): Unit = evt match {
    case c: SaveMail ⇒
      val userOutBox = state.outbox.getOrElse(c.user,ListBuffer())
      state = EmailRepository( state.outbox + (c.user -> userOutBox.+=(c.email)))
  }

  override def receiveCommand: Receive ={
    case c: Preserve_success  =>
      val info = c.info
      val email = Mail(mailFrom="rainservice.com",
        mailto = info.email,mailSubject = "Preserve_success",
        model =Map("username"-> info.username,
                   "startingPlace"->info.startingPlace,
                   "endPlace" ->info.endPlace,
                    "startingTime"->info.startingTime,
                    "date" ->info.date,
                    "seatClass"->info.seatClass,
                     "seatNumber"->info.seatNumber,
                     "price"->info.price))
      persist(SaveMail(c.receiver,email))(updateState)
      sender ! RequestComplete(c.deliveryId,c.requester,c.requestId)
      println("PreservationSuccess------")
      deliver(c.requester.path)(deliveryId=> Response(0, "Success", PreservationSuccess(deliveryId,email)))

    case c:Order_create_success =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = c.info.email,mailSubject = "Order_create_success",
        model =Map("username"-> c.info.username,
          "startingPlace"->c.info.startingPlace,
          "endPlace" ->c.info.endPlace,
          "startingTime"->c.info.startingTime,
          "date" ->c.info.date,
          "seatClass"->c.info.seatClass,
          "seatNumber"->c.info.seatNumber,
          "price"->c.info.price))
      persist(SaveMail(c.receiver,email))(updateState)
      sender ! RequestComplete(c.deliveryId,c.receiver,c.requestId)
      deliver(c.receiver.path)(deliveryId=> Response(0, "Success", OrderCreated(deliveryId,email)))

    case c:Order_changed_success =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = c.info.email,mailSubject = "Order_changed_success",
        model =Map("username"-> c.info.username,
          "startingPlace"->c.info.startingPlace,
          "endPlace" ->c.info.endPlace,
          "startingTime"->c.info.startingTime,
          "date" ->c.info.date,
          "seatClass"->c.info.seatClass,
          "seatNumber"->c.info.seatNumber,
          "price"->c.info.price))
      persist(SaveMail(c.receiver,email))(updateState)
      sender ! RequestComplete(c.deliveryId,c.receiver,c.requestId)
      deliver(c.receiver.path)(deliveryId=> Response(0, "Success", OrderChanged(deliveryId,email)))


    case Order_cancel_success(info: NotifyInfo,receiver: ActorRef,deliveryId,requestId) =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = info.email,mailSubject = "Order_cancel_success",
        model =Map("username"-> info.username,
          "startingPlace"->info.startingPlace,
          "endPlace" ->info.endPlace,
          "startingTime"->info.startingTime,
          "date" ->info.date,
          "seatClass"->info.seatClass,
          "seatNumber"->info.seatNumber,
          "price"->info.price))
      persist(SaveMail(receiver,email))(updateState)
      sender ! RequestComplete(deliveryId,receiver,requestId)
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderCanceled(deliveryId,email)))


    case c:Order_Rebook_success =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = c.info.email,mailSubject = "Order_cancel_success",
        model =Map("username"-> c.info.username,
          "startingPlace"->c.info.startingPlace,
          "endPlace" ->c.info.endPlace,
          "startingTime"->c.info.startingTime,
          "date" ->c.info.date,
          "seatClass"->c.info.seatClass,
          "seatNumber"->c.info.seatNumber,
          "price"->c.info.price))
      persist(SaveMail(c.receiver,email))(updateState)
      sender ! RequestComplete(c.deliveryId,c.receiver,c.requestId)
      deliver(c.receiver.path)(deliveryId=> Response(0, "Success", OrderRebooked(deliveryId,email)))

    case c:Order_Paid_success =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = c.info.email,mailSubject = "Order_cancel_success",
        model =Map("username"-> c.info.username,
          "startingPlace"->c.info.startingPlace,
          "endPlace" ->c.info.endPlace,
          "startingTime"->c.info.startingTime,
          "date" ->c.info.date,
          "seatClass"->c.info.seatClass,
          "seatNumber"->c.info.seatNumber,
          "price"->c.info.price))
      persist(SaveMail(c.receiver,email))(updateState)
      sender ! RequestComplete(c.deliveryId,c.receiver,c.requestId)

      deliver(c.receiver.path)(deliveryId=> Response(0, "Success", OrderPaid(deliveryId,email)))

    case ConfirmMailDelivery(deliverId: Long)=>
      confirmDelivery(deliverId)
  }

}
}