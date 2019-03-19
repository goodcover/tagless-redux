package com.dispalt.taglessKryo.tests

import cats.tagless.autoFunctorK
import com.dispalt.taglessKryo.kryoEncoder

object algs {

  @kryoEncoder
  @autoFunctorK(autoDerivation = false)
  trait SafeAlg[F[_]] {
    def test(i: Int, foo: String): F[Int]
    def test2(i: Int): F[Int]
    def id: F[String]
  }

  object SafeAlg
}
