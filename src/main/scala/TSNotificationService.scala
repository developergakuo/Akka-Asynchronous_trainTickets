object TSNotificationService {

  trait NotificationService {
    def preserve_success(info: Nothing, headers: Nothing): Boolean

    def order_create_success(info: Nothing, headers: Nothing): Boolean

    def order_changed_success(info: Nothing, headers: Nothing): Boolean

    def order_cancel_success(info: Nothing, headers: Nothing): Boolean
  }

}
