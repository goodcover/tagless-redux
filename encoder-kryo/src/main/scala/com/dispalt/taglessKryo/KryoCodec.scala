package com.dispalt.taglessKryo

import com.dispalt.tagless.util.CodecFactory
import com.dispalt.tagless.util.WireProtocol.{Decoder, Encoder}

object KryoCodec extends CodecFactory[KryoImpl] {

  override def encode[A](implicit p: KryoImpl[A]): Encoder[A] = { a: A =>
    p(a)
  }

  override def decode[A](implicit p: KryoImpl[A]): Decoder[A] = { ab: Array[Byte] =>
    p.invert(ab)
  }

}
