
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}


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

  override def persistenceId = "UserService-id"

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
    case Preserve_success(info: NotifyInfo,receiver: ActorRef  )  =>
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
      persist(SaveMail(receiver,email))(updateState)
      deliver(receiver.path)(deliveryId=> Response(0, "Success", PreservationSuccess(deliveryId,email)))

    case Order_create_success(info: NotifyInfo,receiver: ActorRef) =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = info.email,mailSubject = "Order_create_success",
        model =Map("username"-> info.username,
          "startingPlace"->info.startingPlace,
          "endPlace" ->info.endPlace,
          "startingTime"->info.startingTime,
          "date" ->info.date,
          "seatClass"->info.seatClass,
          "seatNumber"->info.seatNumber,
          "price"->info.price))
      persist(SaveMail(receiver,email))(updateState)
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderCreated(deliveryId,email)))

    case Order_changed_success(info: NotifyInfo,receiver: ActorRef ) =>
      val email = Mail(mailFrom="rainservice.com",
        mailto = info.email,mailSubject = "Order_changed_success",
        model =Map("username"-> info.username,
          "startingPlace"->info.startingPlace,
          "endPlace" ->info.endPlace,
          "startingTime"->info.startingTime,
          "date" ->info.date,
          "seatClass"->info.seatClass,
          "seatNumber"->info.seatNumber,
          "price"->info.price))
      persist(SaveMail(receiver,email))(updateState)
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderChanged(deliveryId,email)))


    case Order_cancel_success(info: NotifyInfo,receiver: ActorRef) =>
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
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderCanceled(deliveryId,email)))


    case Order_Rebook_success(info: NotifyInfo,receiver: ActorRef) =>
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
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderRebooked(deliveryId,email)))

    case Order_Paid_success(info: NotifyInfo,receiver: ActorRef) =>
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
      deliver(receiver.path)(deliveryId=> Response(0, "Success", OrderPaid(deliveryId,email)))

    case ConfirmMailDelivery(deliverId: Long)=>
      confirmDelivery(deliverId)
  }

}
}