package akkabank

import java.io.File
import java.util.concurrent.CountDownLatch
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.persistence.cassandra.testkit.CassandraLauncher


object Guardian {
  def apply(): Behavior[String] =
    Behaviors.setup[String] { context =>
//      import akka.util.Timeout
//      import java.time.Instant
//      import scala.concurrent.duration._
//      import scala.util.{Failure, Success}
//      import BankAccount._
//
//      implicit val timeout: Timeout = 3.seconds
//      val ba1 = context.spawn(BankAccount("9"), "bankaccount1")
//      val ba2 = context.spawn(BankAccount("2"), "bankaccount2")
//      val ba3 = context.spawn(BankAccount("3"), "bankaccount3")
//      val ba4 = context.spawn(BankAccount("4"), "bankaccount4")
//
//      context.ask(ba1, SetAccountName("BA1", _: ActorRef[Summary])) { summary =>
//        println("BA summary: " + summary)
//        "aaaaa"
//      }
//
//      val transaction = Transaction(20.00f, "Deposit 20 dollars", Instant.now)
//
//      context.ask(ba1, AddTransaction(transaction, _: ActorRef[Confirmation])) {
//        case Success(confirm) =>
//          println("add transaction:" + confirm)
//          "confirmation"
//        case Failure(ex) => throw ex
//      }
//
//      val transaction2 = Transaction(-130.00f, "Deposit 20 dollars", Instant.now)
//
//      context.ask(ba1, AddTransaction(transaction2, _: ActorRef[Confirmation])) {
//        case Success(confirm) =>
//          println("add transaction2:" + confirm)
//          "confirmation"
//        case Failure(ex) => throw ex
//      }
//
//
//      context.ask(ba1, GetSummary) { summary =>
//        println("AAAA" + summary)
//        "aaff"
//      }
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
        val system = ActorSystem[String](Guardian(), "AkkaBank")
    }
  }


  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }
}

