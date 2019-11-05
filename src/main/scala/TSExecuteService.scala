object TSExecuteService {

  trait ExecuteService {
    def ticketExecute(orderId: String, headers: Nothing): Nothing

    def ticketCollect(orderId: String, headers: Nothing): Nothing
  }

}
