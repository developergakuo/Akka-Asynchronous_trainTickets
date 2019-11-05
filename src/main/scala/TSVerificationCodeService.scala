object TSVerificationCodeService {

  trait VerifyCodeService {
    def getImageCode(width: Int, height: Int, os: Nothing, request: Nothing, response: Nothing, headers: Nothing): Nothing

    def verifyCode(request: Nothing, response: Nothing, receivedCode: String, headers: Nothing): Boolean
  }

}
