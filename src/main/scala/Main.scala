
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.Json
import io.circe.parser._
import com.twitter.util.Await
import com.twitter.util._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service, http}
import com.twitter.conversions.time._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import Model.RequestToDsp
import io.circe


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
      //DSPへのリクエスト
      val requestDspParams: String = RequestToDsp(request.app_id).asJson.toString
      val listOfFutures: Seq[Future[String]] =
        for( i <- dsp ) yield {
          dspRequest( i, "8082", requestDspParams ).map(_.getContentString.asJson.noSpaces)}

      listOfFutures.foreach(x => x.map(y => println(y)))

/* これをうまいこと使うといいかも?
      listOfFutures onComplete {
        case Success(posts) => for (post <- posts) println(post)
        case Failure(t) => println("エラーが発生した: " + t.getMessage)
      }
      */

      //ResponseFromへの当てはめ List[ResponseFromDsp]
      //下のFutureを取り除く方法
      val listOfResponseFromDsp = for(i <- listOfFutures)yield{decode[List[ResponseFromDsp]](i)}
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

  /* DSPのアドレスを別ファイルから読み込み
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