import TSCommon.Commons._
import InputData._
import akka.persistence._
object TSAuthService {
  case class UsersRepository(users: Map[Int,User])

  class AuthService extends PersistentActor {
    var state = UsersRepository(users.zipWithIndex.map(a=>a._2 + 1->a._1).toMap)

    override def preStart(): Unit = {
      println("Client prestart")
      super.preStart()
    }

    override def postRestart(reason: Throwable): Unit = {
      println("Client post restart")
      println(reason)
      super.postRestart(reason)
    }

    override def persistenceId = "AuthService-id"

    override def recovery: Recovery = super.recovery

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, offeredSnapshot: UsersRepository) ⇒ state = offeredSnapshot
      case RecoveryCompleted =>
        println("Client RecoveryCompleted")
      case x: Evt ⇒
        println("recovering: " + x)
        updateState(x)
    }

    def updateState(evt: Evt): Unit = evt match {
      case c: SaveUser ⇒
        state = UsersRepository( state.users + (c.user.userId -> c.user))
      case c: DeleteUserByUserId =>
        state = UsersRepository( state.users -  c.userId)
    }


    override def receiveCommand: Receive = {
      //request for goods from stockActor
      case c:SaveUser =>
        persistAsync(c)(updateState)
        sender() ! Response(0,"success",None)

      case GetAllUsers =>
        sender() ! Response(0,"success",UsersRepository)

      case c:DeleteUserByUserId =>
        persistAsync(c)(updateState)
        sender() ! Response(0,"success",None)
    }

  }
}
