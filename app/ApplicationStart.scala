import org.flywaydb.core.Flyway
import com.typesafe.config.Config
import javax.inject.Inject
import javax.inject.Singleton
import play.api.Logger

@Singleton
class ApplicationStart @Inject() (config: Config) {
  private val log = Logger(this.getClass)

  private val flyway = Flyway.configure().dataSource(
    config.getString("dbinfo.properties.url"),
    config.getString("dbinfo.properties.user"),
    config.getString("dbinfo.properties.password"))
    .locations("filesystem:" + config.getString("migrationLocation"))
    .load()

  try flyway.migrate
  catch {
    case e: Exception =>
      log.error(s"The Flyway migrations failed: $e")
      System.exit(500)
  }
}