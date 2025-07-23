package com.goodcover.tagless.pekko

import com.goodcover.tagless.util.WireProtocol
import org.apache.pekko.actor.ActorSystem

// Another test algebra to demonstrate extensibility
trait UserAlg2[F[_]]:
  def getUser(id: Long): F[String]
  def createUser(name: String, email: String): F[Long]
  def deleteUser(id: Long): F[Unit]
  def listUsers: F[List[String]]

object UserAlg2 {
  given mkWireProtocol(using system: ActorSystem): WireProtocol[UserAlg2] = MacroPekkoWireProtocol.derive[UserAlg2]
}
