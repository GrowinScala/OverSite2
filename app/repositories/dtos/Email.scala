package repositories.dtos
//import java.util.Date
//import java.sql.Timestamp

/* We use a default id so that we can ommit the id on the '+=' method.
We use a negative number because we would get an error if this default value
were to be accepted*/

case class Email(emailId: Int = -1, fromAddressId: Int, chatId: Int, body: String, date: String, sent: Int)

/*
implicit val dateColumnType: BaseColumnType[Date] = MappedColumnType.base[Date, Timestamp](dateToTimestamp, timestampToDate)
private def dateToTimestamp(date: Date): Timestamp = new Timestamp(date.getTime)
private def timestampToDate(timestamp: Timestamp): Date = new Date(timestamp.getTime)*/
