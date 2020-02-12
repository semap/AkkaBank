package akkabank

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.util.Timeout
import akkabank.BankAccount.{Confirmation, Deposit, OpenAccount, SetAccountName}
import akkabank.domain.Money

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

object Guardian {
  def apply(httpPort: Int): Behavior[String] =
    Behaviors.setup[String] { context =>
//      val httpPort = context.system.settings.config.getInt("shopping.http.port")

      implicit val system = context.system
      val cluster = Cluster(system)

      BankAccount.init(system)
      val routes = new BankServerRoutes()
      new BankServer(routes.accounts, httpPort, system).start()
      Behaviors.empty
    }
}

object Main {

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        val httpPort = ("80" + portString.takeRight(2)).toInt
        startNode(port, httpPort)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

      case None =>
        startNode(2551, 8051)
        startNode(2552, 8052)

    }
  }

  private def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }

  private def startNode(port: Int, httpPort: Int): Unit = {
    ActorSystem(Guardian(httpPort), "AkkaBank", config(port, httpPort))
  }

  def config(port: Int, httpPort: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akkaBank.http.port = $httpPort
       """).withFallback(ConfigFactory.load())
}

