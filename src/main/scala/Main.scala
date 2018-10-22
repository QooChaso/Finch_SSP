
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe.syntax._
import io.circe.generic.auto._
import com.twitter.util.Await
import com.twitter.util._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service, http}
import com.twitter.conversions.time._
import Model.RequestToDsp
import io.circe.Json
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source


object Main extends App {

  //val dsp = Seq("127.0.0.1", "127.0.0.2", "127.0.0.3")
  val dsp = Seq("localhost", "localhost", "localhost")

  //case class引数名とcurlで送るjsonのkeyは一致させないとうまく動かない。
  case class RequestFromSdk(app_id: Int)
  case class ResponseToSdk(url: String)
  case class ResponseFromDsp(request_id: String, url: String, price: Float)
  case class WinNotice(request_id: String, price: Float)

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { request: RequestFromSdk =>
      println("Received request from SDK")
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString

      //val aa: Future[Response] = dspRequest("localhost", "8082", requestDspParams)
      val listOfFutures: Seq[Future[Response]] =
        for( i <- dsp ) yield {
            dspRequest( i, "8082", requestDspParams )
        }


      //listOfFuturesの返り値のpromiseが意味不
      //追記 レスポンス帰ってきた
      listOfFutures.foreach(x => x.map(y => println(y.getContentString)))
      //aa.map(x => println(x.status+", "+x.toString+", "+x.getContentString))

      //ResponseFromへの当てはめ
      // 最高額探索
      //セカンドプライスを計算
      //落札DSPにWinNotice送信
      //ログ残し

      //SDKにレスポンス送信
      //ここにコンパニオンオブジェクトのapplyを定義したコンパニオンクラスを渡すとうまく実行されない
      Ok(ResponseToSdk("http://warosu"))
    }

  //DSPへHTTP通信
  def dspRequest(host: String, port: String, requestContent: String): Future[Response] ={
    val requestHost = s"$host:$port"
    val client: Service[Request, Response] = Http.client.
      withRequestTimeout(100.microsecond)
      .newService(requestHost)

    val request: Request = http.Request(http.Method.Post, "/").host(host)
    request.setContentTypeJson()
    request.setContentString(requestContent)
    client(request)
  }

  //DSPのアドレスを別ファイルから読み込み
  /*
  var dspList = List()
  val dspSource = Source.fromFile("/Users/f_kurabayashi/IdeaProjects/FinchTest/src/main/scala/dsp.txt", "UTF-8")
  //val dspSource = Source.fromFile("dsp.txt")
  dspSource.getLines.foreach{x =>
    dspList :+ x
    println(x)
  }
  dspSource.close
  */

  val routes = index.toService
  val server = Http.server.serve(":8081", routes)

  //接続ができなかった時にtimeoutを投げる
  Await.ready(server)
}