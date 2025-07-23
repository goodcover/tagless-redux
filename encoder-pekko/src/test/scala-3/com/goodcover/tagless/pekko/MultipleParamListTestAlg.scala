package com.goodcover.tagless.pekko

import com.goodcover.tagless.util.WireProtocol
import org.apache.pekko.actor.ActorSystem
import cats.tagless.FunctorK
import cats.tagless.Derive

// Test algebra with multiple parameter lists to verify nested tuple access
trait MultipleParamListTestAlg[F[_]] derives FunctorK:
  def simpleMethod: F[String]
  def singleParamList(value: Int): F[String]
  def multipleParamLists(first: String)(second: Int): F[String]
  def threeParamLists(a: String)(b: Int)(c: Boolean): F[String]
  def mixedParamLists(a: String, b: Int)(c: Boolean): F[String]

object MultipleParamListTestAlg {
  given mkWireProtocol(using system: ActorSystem): WireProtocol[MultipleParamListTestAlg] =
    MacroPekkoWireProtocol.derive[MultipleParamListTestAlg]
}
