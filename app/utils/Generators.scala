package utils

import java.sql.Timestamp
import java.util.UUID.randomUUID

object Generators {

  def newUUID: String = randomUUID().toString
  def currentTimestamp: Timestamp = new Timestamp(System.currentTimeMillis)

}
