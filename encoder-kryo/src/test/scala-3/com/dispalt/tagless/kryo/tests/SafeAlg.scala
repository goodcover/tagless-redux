package com.dispalt.tagless.kryo.tests

import cats.tagless.{ Derive, FunctorK }
import com.dispalt.tagless.kryo.MacroKryoWireProtocol
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.tagless.kryo.Default.kryoInstance

object algs {

  trait SafeAlg[F[_]] {
    def test(i: Int, foo: String): F[Int]
    def test2(i: Int): F[Int]
    def id: F[String]
  }

  //
  object SafeAlg {
    given mkWireProtocol: WireProtocol[SafeAlg] = MacroKryoWireProtocol.derive[SafeAlg]
    given functorK: FunctorK[SafeAlg]           = Derive.functorK
  }
}
