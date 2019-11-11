
import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

implicit val timeout: Timeout = 2.seconds

object TSNotificationService {



class NotificationService extends Actor {
  var receiver: ActorRef = null
  override def receive: Receive ={
    case Preserve_success(info: NotifyInfo) =>
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
      receiver ! (0, "Success", email)

    case Order_create_success(info: NotifyInfo) =>
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
      receiver ! (0, "Success", email)

    case Order_changed_success(info: NotifyInfo) =>
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
      receiver ! (0, "Success", email)


    case Order_cancel_success(info: NotifyInfo) =>
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
      receiver ! (0, "Success", email)


  }

}
}