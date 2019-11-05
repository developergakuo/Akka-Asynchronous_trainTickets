object TSConsignPriceService {

  trait ConsignPriceService {
    def getPriceByWeightAndRegion(weight: Double, isWithinRegion: Boolean, headers: Nothing): Nothing

    def queryPriceInformation(headers: Nothing): Nothing

    def createAndModifyPrice(config: Nothing, headers: Nothing): Nothing

    def getPriceConfig(headers: Nothing): Nothing
  }

}
