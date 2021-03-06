import java.util.Date
import Model.WinNotice
import java.io.{FileOutputStream => FileStream, OutputStreamWriter => StreamWriter}

object WriteLog {
  def write(value: WinNotice): Unit ={
    val fileName = "Log.txt"
    val encode = "UTF-8"
    val append = true
    val time: String = "%tY/%<tm/%<td %<tH:%<tM:%<tS" format new Date
    val fileOutPutStream = new FileStream(fileName, append)
    val writer = new StreamWriter( fileOutPutStream, encode )
    writer.write(time+"---reqest_id : "+value.request_id+"----price : "+value.price+"\n")
    writer.close
  }
}

