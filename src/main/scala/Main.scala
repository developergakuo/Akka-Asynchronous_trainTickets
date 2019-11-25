import Services._
import TSCommon.Commons._
import InputData._
object Main extends App{
  client ! ClientLogin("userName1", "psw1")
  //client ! 1
  // ClientPreserve(startingStaion: String, endSation: String, travelDate: Date, seatsCount: Int,seatType: Int,assuranceType: Int =1,foodType: Int = 1) extends Evt
  //client ! ClientPreserve("station1", "station4", new Date(), 5,SeatClass().secondClass._1,1, 1)
  //client ! ClientRebook(exampleOtherOrder,newTrip)
  //client ! ClientCancelOrder(exampleOrder)
  client ! ClientPay(exampleOtherOrderUnpaid)
  Thread.sleep(3000)
  system.terminate()
}

