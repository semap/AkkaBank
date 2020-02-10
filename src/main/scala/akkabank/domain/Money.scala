package akkabank.domain

import java.util.Currency

import akkabank.CborSerializable

final case class Money(amount: BigDecimal, currency: Currency = Currency.getInstance("USD")) extends CborSerializable {
  def withdraw(amount: BigDecimal): Money = {
    this.copy(this.amount - amount)
  }

  def deposit(amount: BigDecimal): Money = {
    this.copy(this.amount + amount)
  }
}
