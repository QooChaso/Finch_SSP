
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import cats.implicits._
import cats.data.NonEmptyList
import io.circe
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._
import com.twitter.util._
import com.twitter.util.Await
import com.twitter.util.FuturePool
import com.twitter.conversions.time._
import com.twitter.finagle.{Http, Service, http}
import com.twitter.finagle.http.{Request, Response}
import Model._

object Main extends App {
  val dsp: NonEmptyList[String] = NonEmptyList.of("10.100.100.20","10.100.100.22", "10.100.100.24")
  val servicePool: NonEmptyList[Service[Request, Response]] = createClient(dsp)

  //Example curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: NonEmptyList[Future[Either[circe.Error, ResponseFromDsp]]] =
        servicePool.map { x =>
          dspRequest(x, requestDspParams, "/req")
            .map(responseCheck)}

      Future.collect(listOfFutures.toList)
        .flatMap { x =>
          FuturePool.unboundedPool{
            val nel: NonEmptyList[Either[circe.Error, ResponseFromDsp]] = NonEmptyList.fromListUnsafe(x.toList)
            println(nel)
            val dspResList: NonEmptyList[(ResponseFromDsp, Int)] = nel.map(_.right.get).zipWithIndex
            val winDsp: (ResponseFromDsp, Int) = searchWinDsp(dspResList)
            val secondPrice: Float = calcSecondPrice(dspResList, winDsp)
            val win = WinNotice(winDsp._1.request_id, secondPrice)
            dspRequest(servicePool.toList(winDsp._2), win.toString, "/win")
            //WriteLog.write(win)
            Ok(ResponseToSdk(winDsp._1.url))}}}

    val server = Http.server.serve(":8081", index.toService)
    Await.ready(server)

  def createClient(dspSeq: NonEmptyList[String]): NonEmptyList[Service[Request, Response]] ={
    val poolSeq: NonEmptyList[Service[Request, Response]] =
      dsp.map{i =>
        val client: Service[Request, Response] =
          Http.client
            .withRequestTimeout(100.millis)
            .newService(s"$i:8082")
        client}
    poolSeq}

  def dspRequest(client: Service[Request, Response], requestContent: String, reqApi: String): Future[Option[Response]] ={
    val request: Request = http.Request(http.Method.Post, reqApi).host("8082")
    request.setContentTypeJson
    request.setContentString(requestContent)
    client(request)
      .map(Some(_): Option[Response])
      .handle{case _ => None}}

  def responseCheck(res: Option[Response]): Either[circe.Error, ResponseFromDsp] =
    res match {
      case Some(value) => decode[ResponseFromDsp](value.getContentString)
      case None => Right(ResponseFromDsp("-1", "-1", 0))}

  def searchWinDsp(nel: NonEmptyList[(ResponseFromDsp, Int)]): (ResponseFromDsp, Int) ={
    val max: Float = nel.map(x => x._1.price).sorted.reverse.head
    val winIndex: (ResponseFromDsp, Int) =
      nel.find(t => t._1.price == max).get
    winIndex}

  def calcSecondPrice(seq: NonEmptyList[(ResponseFromDsp, Int)], winDsp: (ResponseFromDsp, Int)): Float =
    if(seq.length <= 1) 1
    else seq.filterNot(x => x == winDsp).map(_._1.price).sorted.reverse.head

}