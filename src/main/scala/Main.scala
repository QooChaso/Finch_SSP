import io.finch._
import io.finch.syntax._
import com.twitter.finagle.Http
import com.twitter.util.Await

import scala.io.Source
//どうやら下記のimport文を追加するとエラーが消えるらしい。
import io.circe.generic.auto._
import io.finch.circe._

object Main extends App{

  /*
  val index: Endpoint[String] = get("index") {
    Ok("you called index api")
  }

  関係ないけどhtmlをresponseに乗せてrenderすることも可能
  val index: Endpoint[Response] = get(/) {
    Ok(html.index("Finch rocks!")).toResponse[Text.Html]()
   }
  */

  //case class引数名とcurlで送るjsonのkeyは一致させないとうまく動かない。
  case class RequestFromSdk(app_id: Int)
  case class ResponseToSdk(url: String)
  case class RequestFromDsp(ssp_name: String, request_time: String, request_id: String, app_id: Int)
  case class ResponseToDsp(request_id: String, url: String, price: Float)
  case class WinNotice(request_id: String, price: Float)


  /*
  val dspList = List.empty[String]
  val dspSource = Source.fromFile("dsp.txt", "UTF-8")
  dspSource.getLines.foreach(x => dspList +: x)
  dspSource.close
  */

  //curl http://localhost:8081/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[ResponseToSdk] =
    post("index" :: jsonBody[RequestFromSdk]) { reaquest: RequestFromSdk =>
      //{ 非同期処理
      println("received request from SDK")
      //dspList.foreach(println)
        //{ 非同期処理
          //DSPへのリクエスト送信

          //DSPからのレスポンス受け取り

        //}

        //DSPからのレスポンスから最高額のレスポンスを探す
        //落札DSPにWinNotice送信
        //SDKにレスポンス送信
      //}
      Ok(ResponseToSdk("http://warosu"))
  }

  val routes = index.toService
  val server = Http.server.serve(":8081", routes)

  //接続ができなかった時にtimeoutを投げる
  Await.ready(server)
}