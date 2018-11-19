
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._
import com.twitter.util._
import com.twitter.util.Await
import com.twitter.conversions.time._
import com.twitter.finagle.{Http, Service, http}
import com.twitter.finagle.http.{Request, Response}
import Model._

object Main extends App {
  val dsp = Seq("127.0.0.1")

  //Connection Pooling
  val servicePool: Seq[Service[Request, Response]] = createClient(dsp)

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>

      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: Seq[Future[Either[circe.Error, ResponseFromDsp]]] =
        servicePool.par.map { x =>
          dspRequest(x, requestDspParams, "/req")
            .map(responseCheck)}.seq

      Future.collect(listOfFutures)
        .map { x =>
          val dspResList: Seq[ResponseFromDsp] = x.map(_.right.get)
          val winIndex = searchWinIndex(dspResList)
          val winDsp = dspResList(winIndex)
          val secondPrice: Float = calcSecondPrice(dspResList, winIndex)
          val win = WinNotice(winDsp.request_id, secondPrice)
          dspRequest(servicePool(winIndex), win.toString, "/win")
          //WriteLog.write(win)
          Ok(ResponseToSdk(winDsp.url))}
    }
    val server = Http.server.serve(":8081", index.toService)
    Await.ready(server)

  def createClient(dspSeq: Seq[String]): Seq[Service[Request, Response]] ={
    val poolSeq: Seq[Service[Request, Response]] =
      dsp.map{i =>
        val client: Service[Request, Response] =
          Http.client
            .withRequestTimeout(100.millis)
            .newService(s"$i:8082")
        client}
    poolSeq
  }

  def dspRequest(client: Service[Request, Response], requestContent: String, reqApi: String): Future[Option[Response]] ={
    val request: Request = http.Request(http.Method.Post, reqApi).host("8082")
    request.setContentTypeJson
    request.setContentString(requestContent)
    client(request)
      .map(Some(_): Option[Response])
      .handle{case _ => None}
  }

  def responseCheck(res: Option[Response]): Either[circe.Error, ResponseFromDsp] ={
    res match {
      case Some(value) => decode[ResponseFromDsp](value.getContentString)
      case None => Right(ResponseFromDsp("-1", "-1", 0))
    }
  }

  def searchWinIndex(list: Seq[ResponseFromDsp]): Int ={
    val winIndex: Int = list.indexWhere(_.price == list.map(_.price).max)
    winIndex
  }

  def calcSecondPrice(seq: Seq[ResponseFromDsp], winIndex: Int): Float ={
    if(seq.length <= 1) seq.head.price + 1
    else seq.drop(winIndex).map(_.price).max
  }
}