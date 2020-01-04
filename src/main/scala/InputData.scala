import TSCommon.Commons.{Contacts, FoodStore, Trip, _}
import java.util.{Calendar, Date}

object InputData {
  //trains for the train service repo
 val trains =  List(TrainType(1,50,50,70),TrainType(2,50,50,70), TrainType(3,50,50,70), TrainType(4,50,50,70), TrainType(5,50,50,70),
   TrainType(6,50,50,70), TrainType(7,50,50,70), TrainType(8,50,50,70), TrainType(9,50,50,70),TrainType(10,50,50,70))
// routes for the route service
  //  case class Route(id: Int, stations: List[Int], distances: List[Int],startStationId: Int,terminalStationId: Int )
  val routes =List(Route(1,List(1,2,4),List(4,2,5),1,4), Route(2,List(1,8,4),List(4,2,5),1,4),
    Route(3,List(1,2,4), List(4,2,5),1,4), Route(4,List(2,6,10),List(4,2,5),2,10), Route(5,List(1,2,4,6),List(4,2,5,7),1,6),
    Route(6,List(4,2,9),List(4,2,5),4,9), Route(7,List(5,2,3),List(4,2,5),5,3),
    Route(8,List(7,10,4),List(4,2,5),7,4), Route(9,List(9,2,5),List(4,2,5),9,5), Route(10,List(8,7,1),List(4,2,5),8,1))
  //trips for travel service
  //Trip( tripId: Int,trainTypeId: Int, routeId: Int, startingTime: Date, startingStationId: Int, stationsId: List[Int], terminalStationId: Int)
  val trips = List(Trip(1,1,1,new Date(),1,List(1,2,4),4),Trip(2,2,2,new Date(),1,List(1,8,4),4),
    Trip(3,3,3,new Date(),1,List(1,2,4),4),Trip(4,4,4,new Date(),2,List(2,6,10),10), Trip(5,5,5,new Date(),1,List(1,2,4,6),6),
    Trip(6,6,6,new Date(),4,List(4,2,9),9),Trip(7,7,7,new Date(),5,List(5,2,3),3),Trip(8,8,8,new Date(),7,List(7,10,4),4),
    Trip(9,9,9,new Date(),9,List(9,2,5),5),Trip(10,10,10,new Date(),8,List(8,7,1),1))
  //stations for the station service
  val stations = List(Station(1,"station1",5), Station(2,"station2",5), Station(3,"station3",5), Station(4,"station4",5),
    Station(5,"station5",5), Station(6,"station6",5), Station(7,"station7",5), Station(8,"station8",5), Station(9,"station9",5),
    Station(10,"station10",5))
  //price configs for the price service
  val basicPriceRate = 0.5
  val firstClassPriceRate = 0.8
  val priceConfigs = List(PriceConfig(1,1,1,basicPriceRate,firstClassPriceRate),
    PriceConfig(2,2,2,basicPriceRate,firstClassPriceRate), PriceConfig(3,3,3,basicPriceRate,firstClassPriceRate),
    PriceConfig(4,4,4,basicPriceRate,firstClassPriceRate), PriceConfig(5,5,5,basicPriceRate,firstClassPriceRate),
    PriceConfig(6,6,6,basicPriceRate,firstClassPriceRate), PriceConfig(7,7,7,basicPriceRate,firstClassPriceRate),
    PriceConfig(8,8,8,basicPriceRate,firstClassPriceRate), PriceConfig(9,9,9,basicPriceRate,firstClassPriceRate),
    PriceConfig(10,10,10,basicPriceRate,firstClassPriceRate))
  //food map for food map service
  val trainFoods = List(TrainFood(1,1,List(Food("pasta", 5),Food("Fries",5))), TrainFood(2,2,List(Food("pasta", 5),Food("corn",5))),
    TrainFood(3,3,List(Food("waffles", 5),Food("beans",5))), TrainFood(4,4,List(Food("eggs", 5),Food("Fries",5))),
    TrainFood(5,5,List(Food("pasta", 5),Food("lamb",5))), TrainFood(6,6,List(Food("rice", 5),Food("Fries",5))),
    TrainFood(7,7,List(Food("pasta", 5),Food("turkey",5))), TrainFood(8,8,List(Food("sushi", 5),Food("Fries",5))),
    TrainFood(9,9,List(Food("pizza", 5),Food("chicken",5))), TrainFood(10,10,List(Food("pasta", 5),Food("steak",5))))

  val cal = Calendar.getInstance() // creates calendar
  cal.setTime(new Date())// sets calendar time/date
  cal.add(Calendar.HOUR_OF_DAY, 12) // adds 12 hours
  cal.getTime()
  val foodStores = List(FoodStore(1,1,"Store1","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(2,2,"Store2","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
      FoodStore(3,3,"Store3","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(4,4,"Store4","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
      FoodStore(5,5,"Store5","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(6,6,"Store6","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(7,7,"Store7","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(8,8,"Store8","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(9,9,"Store9","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(10,10,"Store10","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5))),
    FoodStore(1,1,"Store1","0487478415",(new Date(),cal.getTime()),1.0,List(Food("pasta", 5),Food("Fries",5)))
  )

  //contacts for the contact service
  // case class Contacts(id: Int, accountId: Int, name: String, documentType: Int, documentNumber: Int, phoneNumber: String)
  val contacts = List(Contacts(1,1,"contact1",1,1,"0487478415"), Contacts(2,2,"contact1",1,1,"0487478415"),
    Contacts(3,3,"contact1",1,1,"0487478415"), Contacts(4,4,"contact1",1,1,"0487478415"),
    Contacts(5,5,"contact1",1,1,"0487478415"), Contacts(6,6,"contact1",1,1,"0487478415"),
    Contacts(7,7,"contact1",1,1,"0487478415"), Contacts(8,8,"contact1",1,1,"0487478415"),
    Contacts(9,9,"contact1",1,1,"0487478415"))

  //users for the auth service
  val users = List(User(1,"userName1","psw1"), User(2,"userName2","psw2"), User(3,"userName3","psw3"),
    User(4,"userName4","psw4"), User(5,"userName5","psw5"), User(6,"userName6","psw6"), User(7,"userName7","psw7"),
    User(8,"userName8","psw8"), User(9,"userName9","psw9"))

  //UserDto for the user Service
  val userDtos =  List(Account(1,"userName1","psw1","Male",1,1,"userr1@Tservice.com"),
    Account(2,"userName2","psw2","Null",1,1,"userr2@Tservice.com"),
    Account(3,"userName3","psw3","Male",1,1,"userr3@Tservice.com"),
    Account(4,"userName4","psw4","Female",1,1,"userr4@Tservice.com"),
    Account(5,"userName5","psw5","Female",1,1,"userr5@Tservice.com"),
    Account(6,"userName6","psw6","Female",1,1,"userr6@Tservice.com"),
    Account(7,"userName7","psw7","Male",1,1,"userr7@Tservice.com"),
    Account(8,"userName8","psw8","Male",1,1,"userr8@Tservice.com"),
    Account(9,"userName9","psw9","Other",1,1,"userr9@Tservice.com"))
  val money: Double = 100.00
  val money2: Double = 0.00

  val moneyType = MoneyType().D
  val moneys = List(Money2(1,money2,moneyType),Money2(2,money,moneyType),Money2(3,money,moneyType),Money2(4,money,moneyType),Money2(5,money,moneyType),Money2(6,money,moneyType),Money2(7,money,moneyType),Money2(8,money,moneyType),Money2(9,money,moneyType),Money2(10,money,moneyType))
// case  class Order(var id: Int, boughtDate: Date, travelDate: Date, travelTime: Date, accountId: Int,
// contactsName: String, var documentType: Int = -1, var contactsDocumentNumber: Int = -1, var trainNumber: Int = -1,
// var coachNumber: Int = -1, var seatClass: Int = -1, var seatNumber: Int = -1, var from: Int = -1, var to: Int = -1,
// var status: Int = -1, var price: Double = 0.0)
  val oldTrip = Trip(1,1,1,new Date(),1,List(1,2,4),4)
  val newTrip = Trip(2,2,2,new Date(),1,List(1,8,4),4)
  val exampleOrder = Order(1,new Date(),new Date(),new Date(),1,"userName1",1,1,1,1,1,4,1,4,OrderStatus().PAID._1,35)
  val exampleOrderUnpaid = Order(1,new Date(),new Date(),new Date(),1,"userName1",1,1,4,1,1,4,1,8,OrderStatus().NOTPAID._1,35)


  val oldTrip1 = Trip(4,4,4,new Date(),2,List(2,6,10),10)
  val newTrip1 = Trip(3,3,3,new Date(),1,List(1,2,4),4)
  val exampleOtherOrder = Order(1,new Date(),new Date(),new Date(),1,"userName1",1,1,4,1,1,4,1,4,OrderStatus().PAID._1,35)
  val exampleOtherOrderUnpaid = Order(2,new Date(),new Date(),new Date(),1,"userName1",1,1,4,1,1,4,2,10,OrderStatus().NOTPAID._1,35)






}
