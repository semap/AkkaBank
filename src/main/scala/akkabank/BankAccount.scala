package akkabank

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akkabank.domain.Money
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

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

  final case class BankAccountInfo(id: String, accountName: String = "", balance: Money = Money(0)) extends CborSerializable {
    def deposit(money: Money): BankAccountInfo = copy(balance = balance.deposit(money.amount))
    def withdraw(money: Money): BankAccountInfo = copy(balance = balance.withdraw(money.amount))
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    Array(
      new JsonSubTypes.Type(value = classOf[UnInitialized], name="unInitialized"),
      new JsonSubTypes.Type(value = classOf[Active], name = "unInitialized"),
      new JsonSubTypes.Type(value = classOf[Inactive], name = "inactive")))
  trait State extends CborSerializable
  case class UnInitialized(flag: Boolean = false) extends State
  case class Active(bankAccountInfo: BankAccountInfo) extends State
  case class Inactive(bankAccountInfo: BankAccountInfo) extends State

  sealed trait Command extends CborSerializable {
    val replyTo: ActorRef[Confirmation]
  }
  final case class OpenAccount(name: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class CloseAccount(replyTo: ActorRef[Confirmation]) extends Command
  final case class Deposit(amount: Money, transactionId: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class Withdraw(amount: Money, transactionId: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class SetAccountName(name: String, replyTo: ActorRef[Confirmation]) extends Command
  final case class GetSummary(replyTo: ActorRef[Confirmation]) extends Command

  sealed trait Event extends CborSerializable {
    def accountId: String
  }

  final case class AccountOpen(accountId: String, name: String) extends Event
  final case class AccountClosed(accountId: String) extends Event
  final case class Deposited(accountId: String, transactionId: String, money: Money) extends Event
  final case class withdrawn(accountId: String, transactionId: String, money: Money) extends Event
  final case class NameChanged(accountId: String, name: String) extends Event

  sealed trait Confirmation extends CborSerializable
  final case class Accept(summary: State) extends Confirmation
  final case class Reject(reason: String) extends Confirmation

  def apply(accountId: String): Behavior[Command] =
    EventSourcedBehavior(
      PersistenceId(entityTypeKey.name, accountId),
      UnInitialized(),
      (state, command) => commandHandler(accountId, state, command),
      eventHandler
    )

  private def commandHandler(accountId: String, state: State, command: Command): Effect[Event, State] =
    state match {
      case UnInitialized(_) =>
        command match {
          case OpenAccount(name, replyTo) =>
            Effect.persist(AccountOpen(accountId, name))
              .thenReply(replyTo) { Accept(_) }
          case _ =>
            Effect.reply(command.replyTo)(Reject("Account is not open yet."))
        }
      case Active(_) =>
        command match {
          case Deposit(amount, transactionId, replyTo) =>
            if (amount.amount > 0)
              Effect.persist(Deposited(accountId, transactionId, amount))
                .thenReply(replyTo) { Accept(_) }
            else
              Effect.reply(replyTo)(Reject("Deposit amount must be greater than zero."))
          case Withdraw(amount, transactionId, replyTo) =>
            if (amount.amount > 0)
              Effect.persist(withdrawn(accountId, transactionId, amount))
                .thenReply(replyTo) { Accept(_) }
            else
              Effect.reply(replyTo)(Reject("Withdraw amount must be greater than zero."))
          case SetAccountName(name, replyTo) =>
            Effect.persist(NameChanged(accountId, name))
              .thenReply(replyTo) { Accept(_) }
          case GetSummary(replyTo) =>
            Effect.reply(replyTo) { Accept(state) }
          case CloseAccount(replyTo) =>
            Effect.persist(AccountClosed(accountId))
              .thenReply(replyTo) { Accept(_) }
          case _ =>
            Effect.reply(command.replyTo)(Reject("Invalid command"))
        }

      case Inactive(_) =>
        command match {
          case GetSummary(replyTo) =>
            Effect.reply(replyTo)(Accept(state))
          case _ => Effect.reply(command.replyTo)(Reject("Account is in-active"))
        }
    }

  private def eventHandler(state: State, event: Event): State =
    state match {
      // AccountOpen is the only available event for un-initialized state
      case UnInitialized(_) =>
        event match {
          case AccountOpen(id, name) => Active(BankAccountInfo(id = id, accountName = name))
          case _ => state
        }
      case Active(bankAccountInfo) =>
        event match {
          case Deposited(_, _, money) => Active(bankAccountInfo.deposit(money))
          case withdrawn(_, _, money) => Active(bankAccountInfo.withdraw(money))
          case NameChanged(_, name) => Active(bankAccountInfo.copy(accountName = name))
          case AccountClosed(_) => Inactive(bankAccountInfo)
          case _ => state
        }
      // state will never changes when it is in-active
      case Inactive(_) => state
    }

}
