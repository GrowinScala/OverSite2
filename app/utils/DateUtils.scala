package utils

import java.text.SimpleDateFormat
import java.util.Date

object DateUtils {
  def getCurrentDate: String = {
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS")
    dateFormatter.format(new Date())
  }
}
