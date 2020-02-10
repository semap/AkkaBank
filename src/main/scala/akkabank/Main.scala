package akkabank

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.util.Timeout
import akkabank.BankAccount.{Confirmation, Deposit}
import akkabank.domain.Money

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

object Guardian {
  def apply(): Behavior[String] =
    Behaviors.setup[String] { context =>
      val cluster = Cluster(context.system)
      Behaviors.empty
    }
}

object Main {

  def main(args: Array[String]): Unit = {
    args.headOption match {
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
    val system = ActorSystem(Guardian(), "AkkaBank", config(port, httpPort))
    BankAccount.init(system)
    val cluster = Cluster(system)
    implicit val timeout: Timeout = 3.seconds
    implicit val ex = system.executionContext
    val sharding = ClusterSharding(system)

    if (port == 2552) {

      val runnable: Runnable = new Runnable {
        override def run(): Unit = {
          val ba9 = sharding.entityRefFor(BankAccount.entityTypeKey, "48")

          val deposit = Deposit(Money(30.0), "tx0001", _)

          ba9.ask(deposit)
              .map (confirm => println("confirm:" + confirm))
          ba9.ask(BankAccount.GetSummary)
            .map(summary => println("summary:" + summary))

        }
      }
      system.scheduler.scheduleOnce(10.seconds, runnable)
    }
  }

  def config(port: Int, httpPort: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akkaBank.http.port = $httpPort
       """).withFallback(ConfigFactory.load())
}

