package com.goodcover.tagless.pekko

// Another test algebra to test error handling
trait OrderAlg[F[_]]:
  def createOrder(customerId: Long, items: List[String]): F[String]
  def getOrder(orderId: String): F[Option[String]]
  def cancelOrder(orderId: String): F[Boolean]
