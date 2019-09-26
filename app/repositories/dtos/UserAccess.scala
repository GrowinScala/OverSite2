package repositories.dtos

case class UserAccess(address: String, password: String, first_name: Option[String], last_name: Option[String],
  token: Option[String])
