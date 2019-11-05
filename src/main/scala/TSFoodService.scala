object TSFoodService {

  trait FoodService {
    def createFoodOrder(afoi: Nothing, headers: Nothing): Nothing

    def deleteFoodOrder(orderId: String, headers: Nothing): Nothing

    def findByOrderId(orderId: String, headers: Nothing): Nothing

    def updateFoodOrder(updateFoodOrder: Nothing, headers: Nothing): Nothing

    def findAllFoodOrder(headers: Nothing): Nothing

    def getAllFood(date: String, startStation: String, endStation: String, tripId: String, headers: Nothing): Nothing
  }

}
