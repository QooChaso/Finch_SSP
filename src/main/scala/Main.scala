
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._
import com.twitter.util.Await
import com.twitter.util._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service, http}
import com.twitter.conversions.time._
import Model._

object Main extends App {

  //val dsp = Seq("10.100.100.20", "10.100.100.22", "10.100.100.24")
  //val dsp = Seq("127.0.0.1", "127.0.0.1", "127.0.0.1")
  val dsp = Seq("127.0.0.1")

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  implicit val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>

      //DSPへのリクエスト(レスポンスをResponseFromDspへ変換)
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: Seq[Future[Either[circe.Error, ResponseFromDsp]]] =
        for( i <- dsp ) yield {
            dspRequest( i, "8082", requestDspParams, "/req")
              .map(_ match {
                case Some(value) => decode[ResponseFromDsp](value.getContentString)
                case None => Right(ResponseFromDsp("-1", "-1", -1))})}

      val okResponse: Future[ResponseToSdk] =
        Future.collect(listOfFutures)
          .map{ x =>
            x.foreach(println)
            val dspResList: Seq[ResponseFromDsp] =
              for (i <- x.map(_ match { case Right(i) => i})) yield i

            val maxPriceIndex: Int = dspResList
              .indexWhere(_.price == dspResList.map(_.price).max)

            val winDsp: ResponseFromDsp = dspResList(maxPriceIndex)
            val secondPrice: Float = calculateSecondPrice(dspResList)

            //最高額DSPにWinNotice送信
            val win = WinNotice(winDsp.request_id, secondPrice)
            println(win)
            //dspRequest(dsp(maxPriceIndex), "8082", win.toString, "/win")

            //ログ書き込み
            //負荷テストの際にログが残らない現象
            //WriteLog.write(win)
            ResponseToSdk(winDsp.url)
          }
      val sdkResponse = Await.result(okResponse, 100.millis)
      Ok(sdkResponse)
    }

  def dspRequest(host: String, port: String, requestContent: String, reqApi: String): Future[Option[Response]] ={
    val requestHost = s"$host:$port"
    val client: Service[Request, Response] =
      Http.client
        .withRequestTimeout(100.microsecond)
        .newService(requestHost)

    val request: Request = http.Request(http.Method.Post, reqApi).host(host)
    request.setContentTypeJson
    request.setContentString(requestContent)
    client(request)
      .map(Some(_): Option[Response])
      .handle{case _ => None}
  }

  def calculateSecondPrice(seq: Seq[ResponseFromDsp]): Float ={
      seq.filterNot(_.price == seq.map(y => y.price).max)
        .map(_.price)
        .max + 1
  }

  implicit val routes = index.toService
  val server = Http.server.serve(":8081", routes)
  Await.ready(server)
}