package akkabank

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}


object BankAccount {

  val entityKey: String = "bankAccount"
  final case class State(balance: Float = 0.0f, accountName: String = "", transactions: List[Transaction] = Nil) {}

  sealed trait Command
  final case class AddTransaction(transaction: Transaction, replyTo: ActorRef[Confirmation]) extends Command
  final case class SetAccountName(name: String, replyTo: ActorRef[Confirmation]) extends Command

  sealed trait Event {
    def accountId: String
  }

  final case class TransactionAdded(accountId: String, transaction: Transaction) extends Event
  final case class NameChanged(accountId: String, name: String) extends Event

  final case class Summary(balance: Float, accountName: String) {}

  sealed trait Confirmation {}
  final case class Accept(summary: Summary) extends Confirmation
  final case class Reject(reason: String) extends Confirmation

  def apply(accountId: String): Behavior[Command] =
    EventSourcedBehavior(
      PersistenceId(entityKey, accountId),
      State(),
      (state, command) => commandHandler(accountId, state, command),
      eventHandler
    )

  private def commandHandler(accountId: String, state: State, command: Command): Effect[Event, State] =
    command match {
      case AddTransaction(transaction, replyTo) =>
        Effect.persist(TransactionAdded(accountId, transaction))
          .thenReply(replyTo) { state => Accept(Summary(state.balance, state.accountName)) }
      case SetAccountName(name, replyTo) =>
        Effect.persist(NameChanged(accountId, name))
          .thenReply(replyTo) { state => Accept(Summary(state.balance, state.accountName)) }
    }

  private def eventHandler(state: State, event: Event): State =
    event match {
      case TransactionAdded(_, newTransaction) => state.copy(transactions = newTransaction :: state.transactions)
      case NameChanged(_, name) => state.copy(accountName = name)
    }
}
