package com.goodcover.tagless.pekko

// Another test algebra to demonstrate extensibility
trait UserAlg[F[_]]:
  def getUser(id: Long): F[String]
  def createUser(name: String, email: String): F[Long]
  def deleteUser(id: Long): F[Unit]
  def listUsers: F[List[String]]
