import TSCommon.Commons.{Response, _}
import akka.actor.ActorRef
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global

implicit val timeout: Timeout = 2.seconds
import scala.util.{Success, Failure}

object TSUserService {
  case class UserDtoRepository(users: Map[Int,UserDto])
  class UserService extends PersistentActor {
    var state = UserDtoRepository(users = Map())
    var authService: ActorRef = null

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
      case SnapshotOffer(_, offeredSnapshot: UserDtoRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("UserService RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: SaveUserDto ⇒
        val id = state.users.size
        state = UserDtoRepository( state.users + (id -> c.userDto))
      case c: DeleteUserByUserId =>
        state = UserDtoRepository( state.users -  c.userId)
      case c: UpdateUser ⇒
        val id = state.users.size
        state = UserDtoRepository( state.users + (id -> c.user))
    }


    override def receiveCommand: Receive = {
      //request for goods from stockActor
      case c:SaveUserDto =>
        state.users.get(c.userDto.userId) match {
          case Some(_) =>
            sender() ! Response(1,"user with a similar Id already exists",None)
          case None =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.userDto.userId)
        }
      case GetAllUsers =>
        sender() ! Response(0,"success",state.users.values.toList)

      case c:DeleteUserByUserId =>
        state.users.get(c.userId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.userId)
          case None =>
            sender() ! Response(1,"user does not exist",None)
        }
      case c:UpdateUser =>
        state.users.get(c.user.userId) match {
          case Some(_) =>
            persist(c)(updateState)
            sender() ! Response(0,"Success",c.user.userId)
          case None =>
            sender() ! Response(1,"user does not exist",None)
        }
      case c:FindByUserName =>
        var user: Option[User] = None
        for (usr<-state.users.values){
          if(usr.userName == c.userName) user = Some(User(usr.userId,usr.userName,usr.password))
        }
        user match {
          case Some(usr)=>
            sender() ! Response(0,"Success",usr)
          case None =>
            sender() ! Response(1,"No user by that userName",None)
        }
      case c:FindByUserId =>
        state.users.get(c.userId) match {
          case Some(usr) =>
            sender() ! Response(0,"Success",User(usr.userId,usr.userName,usr.password))
          case None =>
            sender() ! Response(1,"user does not exist",None)
        }
      case c:FindByUserId2 =>
        state.users.get(c.userId) match {
          case Some(usr) =>
            sender() ! Response(0,"Success",usr)
          case None =>
            sender() ! Response(1,"user does not exist",None)
        }
      case c:CreateDefaultAuthUser =>
        val response: Future[Any] = authService ? c
        response onComplete{
          case Success(resp) =>
              sender() ! resp.asInstanceOf[Response]
          case Failure(t)=>
            sender() ! Response(1,"Failure",t)
        }
      case c:DeleteUserAuth =>
        val response: Future[Any] = authService ? c
        response onComplete{
          case Success(resp) =>
            sender() ! resp.asInstanceOf[Response]
          case Failure(t)=>
            sender() ! Response(1,"Failure",t)
        }
    }

  }

}
