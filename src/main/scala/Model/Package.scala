package Model

case class RequestFromSdk(app_id: Int)
case class ResponseToSdk(url: String)
case class ResponseFromDsp(request_id: String, url: String, price: Float)
case class WinNotice(request_id: String, price: Float)