import TSCommon.Commons.{Response, _}
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


object TSAdminUserService {


  class AdminUserService(userService: ActorRef) extends Actor {

    override def receive: Receive = {
      case GetAllUsers =>
        var users: Option[List[Account]] = None
        val response: Future[Any] = userService ? Account
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) users = Some(res.asInstanceOf[Response].data.asInstanceOf[List[Account]])
            else users = None
          case Failure(_) =>
            users = None
        }
        users match{
          case Some(i) =>
            sender() ! Response(0,"Success",i)
          case None =>
            sender() ! Response(1,"Error",None)
        }

      case c:DeleteUser =>
        val response: Future[Any] = userService ? DeleteUserByUserId(c.userId)
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }


      case c:UpdateUser=>
        val response: Future[Any] = userService ? c
        response onComplete {
          case Success(res) =>
            if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
            else sender() ! Response(1,"Error",None)
          case Failure(_) =>
            sender() ! Response(1,"Error",None)
        }
        case c:AddUser =>
          val response: Future[Any] = userService ? SaveUserDto(c.userDto)
          response onComplete {
            case Success(res) =>
              if (res.asInstanceOf[Response].status == 0) sender() ! Response(0,"Success",None)
              else sender() ! Response(1,"Error",None)
            case Failure(_) =>
              sender() ! Response(1,"Error",None)
          }

    }
  }

}



