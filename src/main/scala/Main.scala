import io.finch._, io.finch.syntax._
import com.twitter.finagle.Http
import com.twitter.util.Await
//どうやら下記のimport文を追加するとエラーが消えるらしい。
import io.circe.generic.auto._
import io.finch.circe._

object Main extends App{

  /*
  val index: Endpoint[String] = get("index") {

    Ok("you called index api")
  }
  */

  /*　関係ないけどhtmlをresponseに乗せてrenderすることも可能
  val index: Endpoint[Response] = get(/) {
    Ok(html.index("Finch rocks!")).toResponse[Text.Html]()
   }
  */

  //curl http://localhost:8080/index -X POST -H "Content-Type: application/json" -d '{"app_id": 9999}'
  val index: Endpoint[String] = post("index" :: jsonBody[Int]) { apiId: Int =>
    println("received request")

    Ok("you called index api by POST app_id = "+apiId)
  }


  val routes = index.toService

  val server = Http.server.serve(":8081", routes)

  //接続ができなかった時にtimeoutを投げる
  Await.ready(server)
}