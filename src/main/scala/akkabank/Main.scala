package akkabank

import java.io.File

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.persistence.cassandra.testkit.CassandraLauncher


object Guardian {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.spawn(BankAccount("1"), "bankaccount1")
      context.spawn(BankAccount("2"), "bankaccount2")
      context.spawn(BankAccount("3"), "bankaccount3")
      context.spawn(BankAccount("4"), "bankaccount4")

      Behaviors.empty
    }
}

object Main extends App {
  startCassandraDatabase()

  val system = ActorSystem[Nothing](Guardian(), "AkkaBank")


  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }
}

