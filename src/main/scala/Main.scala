import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import doobie.util.transactor.Transactor
import monix.eval.Task
import monix.execution.Scheduler
import org.flywaydb.core.Flyway
import pureconfig.ConfigSource
import repositories.{AccountsRepository, BalanceTransactionsRepository, CategoriesRepository}
import routes.{AccountsRoutes, BalanceTransactionsRoutes, CategoriesRoutes}
import services.{AccountsService, BalanceTransactionsService, CategoriesService}

import scala.io.StdIn

object Main {

  def main(args: Array[String]): Unit = {


    implicit val system: ActorSystem = ActorSystem("my-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    val dbConfig = Settings.config.db

    Flyway.configure.dataSource(dbConfig.url, dbConfig.user, dbConfig.password).load.migrate()

    implicit val transactor: Transactor[Task] = Transactor.fromDriverManager(
      dbConfig.driver, dbConfig.effectiveUrl, dbConfig.user, dbConfig.password
    )

    val logger = Logger("main")
    val categoriesRepository = new CategoriesRepository
    val categoriesService = new CategoriesService(categoriesRepository)
    val categoriesRoutes = new CategoriesRoutes(categoriesService).routes
    val accountsRepository = new AccountsRepository
    val accountsService = new AccountsService(accountsRepository)
    val accountsRoutes = new AccountsRoutes(accountsService).routes

    val balanceTransactionsRepository = new BalanceTransactionsRepository
    val balanceTransactionsService = new BalanceTransactionsService(balanceTransactionsRepository)
    val balanceTransactionsRoutes = new BalanceTransactionsRoutes(balanceTransactionsService).routes

    val route = categoriesRoutes ~ accountsRoutes ~ balanceTransactionsRoutes

    val bindingFuture = Http().bindAndHandle(route, "localhost", 3000)

    logger.info("Server is running at http://localhost:3000")

    StdIn.readLine()

    bindingFuture.flatMap(_.unbind())
      .onComplete(_ => system.terminate())

  }
}
