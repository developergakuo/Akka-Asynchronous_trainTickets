object TSConfigService {

  trait ConfigService {
    def create(info: Nothing, headers: Nothing): Nothing

    def update(info: Nothing, headers: Nothing): Nothing

    //    Config retrieve(String name, HttpHeaders headers);
    def query(name: String, headers: Nothing): Nothing

    def delete(name: String, headers: Nothing): Nothing

    def queryAll(headers: Nothing): Nothing
  }

}
