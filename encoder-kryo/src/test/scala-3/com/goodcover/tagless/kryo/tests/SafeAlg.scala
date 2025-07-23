package com.goodcover.tagless.kryo.tests

import cats.tagless.{ Derive, FunctorK }
import com.goodcover.tagless.kryo.MacroKryoWireProtocol
import com.goodcover.tagless.util.WireProtocol
import com.goodcover.tagless.kryo.Default.kryoInstance

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
