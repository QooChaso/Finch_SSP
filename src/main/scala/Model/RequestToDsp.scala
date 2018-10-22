package Model

import java.util.Date

case class RequestToDsp(
  ssp_name: String,
  request_time: String,
  request_id: String,
  app_id: Int)

object RequestToDsp{

  def apply(request_id: Int): RequestToDsp ={
    val ssp_name = "f_kurabayashi"
    val time: String = "%tY/%<tm/%<td %<tH:%<tM:%<tS" format new Date
    val uuid = java.util.UUID.randomUUID.toString
    new RequestToDsp(ssp_name, time, uuid, request_id)
  }

}