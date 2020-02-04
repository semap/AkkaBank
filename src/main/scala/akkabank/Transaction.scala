package akkabank

import java.time.Instant

final case class Transaction(amount: Float, description: String, timestamp: Instant)
