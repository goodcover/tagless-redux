package com.dispalt.tagless.kryo

import com.dispalt.tagless.WireProtocolKryoLike
import com.dispalt.tagless.util.WireProtocol
import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

import scala.annotation.experimental

@experimental
object MacroKryoWireProtocol:
  inline def derive[Alg[_[_]]](using ScalaKryoSerializer): WireProtocol[Alg] = ${
    WireProtocolKryoLike.wireProtocol[Alg, KryoImpl, KryoCodec]('{ KryoCodec })
  }
