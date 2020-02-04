package akkabank

import java.time.Instant

case class Transaction(amount: Float, description: String, timestamp: Instant)
