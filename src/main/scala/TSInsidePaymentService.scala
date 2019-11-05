object TSInsidePaymentService {

  trait InsidePaymentService {
    def pay(info: Nothing, headers: Nothing): Nothing

    def createAccount(info: Nothing, headers: Nothing): Nothing

    def addMoney(userId: String, money: String, headers: Nothing): Nothing

    def queryPayment(headers: Nothing): Nothing

    def queryAccount(headers: Nothing): Nothing

    def drawBack(userId: String, money: String, headers: Nothing): Nothing

    def payDifference(info: Nothing, headers: Nothing): Nothing

    def queryAddMoney(headers: Nothing): Nothing

    def initPayment(payment: Nothing, headers: Nothing): Unit
  }
}
