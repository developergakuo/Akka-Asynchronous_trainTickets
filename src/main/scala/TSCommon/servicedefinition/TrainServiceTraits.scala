package TSCommon.servicedefinition

import TSCommon.Commons.{Response, User}

object TrainServiceTraits {
  trait UserServiceTrait {
    def saveUser(user: User): Response
    def getAllUser(): List[User]
    //def createDefaultAuthUser(dto: Nothing): BIConversion.User
    def deleteByUserId(userId: Int): Response
  }
}

