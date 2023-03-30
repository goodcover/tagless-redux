package com.dispalt.taglessBoopickle

import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite
import java.util.UUID

import cats.tagless.{Derive, FunctorK}
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.taglessBoopickle.boopickleTests.FooId

object boopickleTests {
  final case class FooId(value: UUID) extends AnyVal
}

class boopickleTests extends AnyFunSuite with Matchers {
  import boopickle.Default._

  trait Foo[K, F[_]] {
    def include(i: K): F[Unit]
    def scdsc(s: String): F[K]
    def id: F[FooId]
  }

  object Foo {
    implicit def functorK[K]: FunctorK[Foo[K, *[_]]]                  = Derive.functorK
    implicit def wireProtocol[K: Pickler]: WireProtocol[Foo[K, *[_]]] = BoopickleWireProtocol.derive
  }

}
