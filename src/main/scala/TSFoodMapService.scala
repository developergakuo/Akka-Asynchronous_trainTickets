object TSFoodMapService {

  trait FoodMapService { // create data
    def createFoodStore(fs: Nothing, headers: Nothing): Nothing

    def createTrainFood(tf: Nothing, headers: Nothing): Nothing

    // query all food
    def listFoodStores(headers: Nothing): Nothing

    def listTrainFood(headers: Nothing): Nothing

    // query according id
    def listFoodStoresByStationId(stationId: String, headers: Nothing): Nothing

    def listTrainFoodByTripId(tripId: String, headers: Nothing): Nothing

    def getFoodStoresByStationIds(stationIds: Nothing): Nothing
  }
}
