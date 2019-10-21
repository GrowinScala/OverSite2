package utils

import java.time._
import java.time.format.DateTimeFormatter

object DateUtils {
  val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def getCurrentDate: String = dateFormatter.format(LocalDateTime.now)

}
