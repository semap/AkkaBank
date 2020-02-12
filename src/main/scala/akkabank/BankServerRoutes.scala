package akkabank

import java.util.{Currency, UUID}

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, concat, delete, entity, get, onSuccess, path, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import akkabank.BankAccount.{Accept, Active, BankAccountInfo, CloseAccount, Deposit, GetSummary, Inactive, Reject, SetAccountName, UnInitialized, Withdraw}
import akkabank.BankServerRoutes.{AccountEvent, OpenAccount, UpdateAccount}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.PathMatchers.Segment
import akkabank.domain.Money
import spray.json.DefaultJsonProtocol._
import spray.json.{DeserializationException, JsString, JsValue, JsonFormat}

import scala.concurrent.Future

object BankServerRoutes {
  final case class OpenAccount(name: String)
  final case class UpdateAccount(name: String)
  final case class AccountEvent(`type`: String, amount: BigDecimal)
}



class BankServerRoutes(implicit system: ActorSystem[_]) {

  private val sharding = ClusterSharding(system)
  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("akkaBank.askTimeout"))
  implicit private val ec = system.executionContext

  import JsonFormats._

  val accounts: Route = pathPrefix("accounts") {
    concat(
      post {
        entity(as[OpenAccount]) { data =>
          val id = UUID.randomUUID().toString
          val entityRef = sharding.entityRefFor(BankAccount.entityTypeKey, id)
          val command = BankAccount.OpenAccount(data.name, _)
          val reply: Future[BankAccount.Confirmation] = entityRef.ask(command)
          onSuccess(reply) {
            case Accept(Active(bankAccountInfo)) =>
              complete(StatusCodes.OK -> bankAccountInfo)
            case Reject(reason) =>
              complete(StatusCodes.BadRequest, reason)
          }
        }
      }, pathPrefix(Segment) { accountId =>
        concat(
          get {
            val entityRef = sharding.entityRefFor(BankAccount.entityTypeKey, accountId)
            onSuccess(entityRef.ask(GetSummary)) {
              case Accept(Active(bankAccountInfo)) =>
                complete(StatusCodes.OK -> bankAccountInfo)
              case Reject(reason) =>
                complete(StatusCodes.BadRequest, reason)
            }
          },
          put {
            entity(as[UpdateAccount])  { data =>
              val entityRef = sharding.entityRefFor(BankAccount.entityTypeKey, accountId)
              val setAccountName = SetAccountName(data.name, _)
              onSuccess(entityRef.ask(setAccountName)) {
                case Accept(Active(bankAccountInfo)) =>
                  complete(StatusCodes.OK -> bankAccountInfo)
                case Reject(reason) =>
                  complete(StatusCodes.BadRequest, reason)
              }
            }
          },
          delete {
            val entityRef = sharding.entityRefFor(BankAccount.entityTypeKey, accountId)
            onSuccess(entityRef.ask(CloseAccount)) {
              case Accept(Inactive(bankAccountInfo)) =>
                complete(StatusCodes.OK -> bankAccountInfo)
              case Reject(reason) =>
                complete(StatusCodes.BadRequest, reason)
            }
          },
          path("events") {
            post {
              entity(as[AccountEvent]) { data =>
                val f = Future.successful(data)
                  .map {
                    val transactionId = UUID.randomUUID().toString
                     _.`type` match {
                       case "deposit" => Deposit(Money(data.amount), transactionId, _)
                       case "withdraw" => Withdraw(Money(data.amount), transactionId, _)
                       case _ => throw new RuntimeException("Invalid")
                     }
                  }
                  .flatMap { sharding.entityRefFor(BankAccount.entityTypeKey, accountId).ask(_) }

                onSuccess(f) {
                  case Accept(Active(bankAccountInfo)) =>
                    complete(StatusCodes.OK -> bankAccountInfo)
                  case Reject(reason) =>
                    complete(StatusCodes.BadRequest, reason)
                }
              }
            }
          }
        )
      }

    )
  }
}

object JsonFormats {

  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._
  import CustomJsonFormats._

  implicit val openAccountFormat: RootJsonFormat[BankServerRoutes.OpenAccount] = jsonFormat1(BankServerRoutes.OpenAccount)
  implicit val updateAccountFormat: RootJsonFormat[BankServerRoutes.UpdateAccount] = jsonFormat1(BankServerRoutes.UpdateAccount)
  implicit val accountEventFormat: RootJsonFormat[BankServerRoutes.AccountEvent] = jsonFormat2(BankServerRoutes.AccountEvent)

  implicit val currencyFormat: JsonFormat[Currency] = CurrencyJsonFormat
  implicit val moneyFormat: JsonFormat[Money] = jsonFormat2(Money)
  implicit val bankAccountInfoFormat: RootJsonFormat[BankAccount.BankAccountInfo] = jsonFormat3(BankAccount.BankAccountInfo)

}

object CustomJsonFormats {
  import spray.json._
  implicit object CurrencyJsonFormat extends JsonFormat[Currency] {
    def write(x: Currency) = {
      require(x ne null)
      JsString(x.getCurrencyCode)
    }
    def read(value: JsValue) = value match {
      case JsString(x) => Currency.getInstance(x)
      case x => deserializationError("Expected String as JsString, but got " + x)
    }
  }
}