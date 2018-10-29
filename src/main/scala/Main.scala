
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

  val dsp = Seq("127.0.0.1", "127.0.0.1", "127.0.0.1")

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>
      println("Call index")

      //DSPへのリクエスト(レスポンスをResponseFromDspへ変換)
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: Seq[Future[Either[circe.Error, ResponseFromDsp]]] =
        for( i <- dsp )
          yield {
            dspRequest( i, "8082", requestDspParams )
            .map(_.getContentString)
            .map(x => decode[ResponseFromDsp](x))}

      val futureOfList: Future[Seq[Either[circe.Error, ResponseFromDsp]]] = Future.collect(listOfFutures)

      val OkResponse: Future[ResponseToSdk] = futureOfList.map{ x =>
        val dspResList: Seq[ResponseFromDsp] =
          for (i <- x.map(_ match { case Right(i) => i})) yield i

        val maxPriceIndex: Int = dspResList.indexWhere(_.price == dspResList.map(_.price).max)

        val winDsp: ResponseFromDsp = dspResList(maxPriceIndex)
        val secondPrice: Float = calculateSecondPrice(dspResList)

        //最高額DSPにWinNotice送信
        val win = WinNotice(winDsp.request_id, secondPrice)
        println(win)
        //dspRequest(dsp(maxPriceIndex), "8082", win.toString)

        //ログ書き込み
        WriteLog.write(win)

        ResponseToSdk(winDsp.url)
      }

      val sdkResponse = Await.result(OkResponse, 100 millis)
      Ok(sdkResponse)
    }

  //DSPへHTTP通信
  def dspRequest(host: String, port: String, requestContent: String): Future[Response] ={
    val requestHost = s"$host:$port"
    val client: Service[Request, Response] = Http.client
      .withRequestTimeout(200.microsecond)
      .newService(requestHost)

    val request: Request = http.Request(http.Method.Post, "/").host(host)
    request.setContentTypeJson()
    request.setContentString(requestContent)
    client(request)
  }

  def calculateSecondPrice(seq: Seq[ResponseFromDsp]): Float ={
    seq.filterNot(n =>
      n.price == seq.map(y => y.price).max)
      .map(_.price)
      .max + 1
  }

  val routes = index.toService
  val server = Http.server.serve(":8081", routes)
  Await.ready(server)
}