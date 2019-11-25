
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


object TSNotificationService {

class NotificationService extends Actor {

  override def receive: Receive ={
    case Preserve_success(info: NotifyInfo,receiver: ActorRef  ) =>
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
      receiver ! Response(0, "Success", PreservationSuccess(email))

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
      receiver ! Response(0, "Success", OrderCreated(email))

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
      receiver ! Response(0, "Success", OrderChanged(email))

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
      receiver ! Response(0, "Success", OrderCanceled(email))
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
      receiver ! Response(0, "Success", OrderRebooked(email))
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
      receiver ! Response(0, "Success", OrderPaid(email))
  }

}
}