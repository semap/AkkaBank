package akkabank

import java.util.{Currency, Locale}

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akkabank.domain.Money

object BankAccount {

  val entityTypeKey = EntityTypeKey[Command]("bankAccount")

  // init cluster sharding
  def init(system: ActorSystem[_]) = {
    ClusterSharding(system)
      .init(
        Entity(entityTypeKey) { entityContext =>
          BankAccount(entityContext.entityId)
        }.withRole("accounts")
      )
  }

  final case class State(accountName: String = "", balance: Money = Money(0)) extends CborSerializable {
    def deposit(money: Money): State = copy(balance = balance.deposit(money.amount))
    def withdraw(money: Money): State = copy(balance = balance.withdraw(money.amount))
  }

  sealed trait Command extends CborSerializable
  final case class Deposit(amount: Money, transactionId: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class Withdraw(amount: Money, transactionId: String, replayTo: ActorRef[Confirmation]) extends Command
  final case class SetAccountName(name: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class GetSummary(replyTo: ActorRef[Confirmation]) extends Command

  sealed trait Event extends CborSerializable {
    def accountId: String
  }

  final case class Deposited(accountId: String, transactionId: String, money: Money) extends Event
  final case class withdrawn(accountId: String, transactionId: String, money: Money) extends Event
  final case class NameChanged(accountId: String, name: String) extends Event

  final case class Summary(balance: Float, accountName: String) extends CborSerializable {}

  sealed trait Confirmation extends CborSerializable
  final case class Accept(summary: State) extends Confirmation
  final case class Reject(reason: String) extends Confirmation

  def apply(accountId: String): Behavior[Command] =
    EventSourcedBehavior(
      PersistenceId(entityTypeKey.name, accountId),
      State(),
      (state, command) => commandHandler(accountId, state, command),
      eventHandler
    )

  private def commandHandler(accountId: String, state: State, command: Command): Effect[Event, State] =
    command match {
      case Deposit(amount, transactionId, replyTo) =>
        if (amount.amount > 0)
          Effect.persist(Deposited(accountId, transactionId, amount))
            .thenReply(replyTo) { state => Accept(state) }
        else
          Effect.reply(replyTo)(Reject("Deposit amount must be greater than zero."))
      case Withdraw(amount, transactionId, replyTo) =>
        if (amount.amount > 0)
          Effect.persist(withdrawn(accountId, transactionId, amount))
            .thenReply(replyTo) { state => Accept(state) }
        else
          Effect.reply(replyTo) (Reject("Withdraw amount must be greater than zero."))
      case SetAccountName(name, replyTo) =>
        Effect.persist(NameChanged(accountId, name))
          .thenReply(replyTo) { Accept(_) }
      case GetSummary(replyTo) =>
        Effect.reply(replyTo) { Accept(state) }
    }

  private def eventHandler(state: State, event: Event): State =
    event match {
      case Deposited(_, _, money) => state.deposit(money)
      case withdrawn(_, _, money) => state.withdraw(money)
      case NameChanged(_, name) => state.copy(accountName = name)
    }
}
