
import Model.WinNotice
import java.io.{FileOutputStream => FileStream, OutputStreamWriter => StreamWriter}

object WriteLog {
  //ログファイルに書き込み
  def write(value: WinNotice): Unit ={
    val fileName = "Log.txt"
    val encode = "UTF-8"
    val append = true
    // 書き込み処理
    val fileOutPutStream = new FileStream(fileName, append)
    val writer = new StreamWriter( fileOutPutStream, encode )
    writer.write("reqest_id : "+value.request_id+"-----price : "+value.price+"\n")
    writer.close
  }
}

