
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
  val dsp = Seq("127.0.0.1", "127.0.0.1")

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>
      //DSPへのリクエスト(レスポンスをResponseFromDspへ変換)
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: Seq[Future[Either[circe.Error, ResponseFromDsp]]] =
        dsp.par.map { x =>
          dspRequest(x, "8082", requestDspParams, "/req")
            .map(responseCheck)}.seq

      Future.collect(listOfFutures)
        .map { x =>
          val dspResList: Seq[ResponseFromDsp] = x.map(_.right.get)
          val winDsp = searchWinDsp(dspResList)
          val secondPrice: Float = calcSecondPrice(dspResList)
          val win = WinNotice(winDsp.request_id, secondPrice)
          dspRequest(dsp(searchWinIndex(dspResList)), "8082", win.toString, "/win")
          //WriteLog.write(win)
          Ok(ResponseToSdk(winDsp.url))}
    }
    val server = Http.server.serve(":8081", index.toService)
    Await.ready(server)

  //clientは使い回しができるのでいちいちserviceを生成する必要がない
  //このclientは同期的に処理されるらしい
  def dspRequest(host: String, port: String, requestContent: String, reqApi: String): Future[Option[Response]] ={
    val requestHost = s"$host:$port"
    val client: Service[Request, Response] =
      Http.client
        .withRequestTimeout(100.millis)
        .newService(requestHost)

    val request: Request = http.Request(http.Method.Post, reqApi).host(host)
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

  def searchWinDsp(list: Seq[ResponseFromDsp]): ResponseFromDsp ={
    val winIndex = searchWinIndex(list)
    val winDsp: ResponseFromDsp = list(winIndex)
    winDsp
  }

  def calcSecondPrice(seq: Seq[ResponseFromDsp]): Float ={
    if(seq.length <= 1) seq.head.price + 1
    else seq
      .filterNot(_.price == seq.map(y => y.price).max)
      .map(_.price)
      .max + 1
  }
}