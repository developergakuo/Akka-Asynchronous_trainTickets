object TSConsignService {

  import java.util.UUID

  trait ConsignService {
    def insertConsignRecord(consignRequest: Nothing, headers: Nothing): Nothing

    def updateConsignRecord(consignRequest: Nothing, headers: Nothing): Nothing

    def queryByAccountId(accountId: UUID, headers: Nothing): Nothing

    def queryByOrderId(orderId: UUID, headers: Nothing): Nothing

    def queryByConsignee(consignee: String, headers: Nothing): Nothing
  }

}
