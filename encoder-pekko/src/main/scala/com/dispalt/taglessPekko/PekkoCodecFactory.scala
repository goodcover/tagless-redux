package com.dispalt.taglessPekko

import com.dispalt.tagless.util.{CodecFactory, WireProtocol}

object PekkoCodecFactory extends CodecFactory[PekkoImpl] {
  override def encode[A](implicit p: PekkoImpl[A]): WireProtocol.Encoder[A] = { (a: A) => p.encode(a) }

  override def decode[A](implicit p: PekkoImpl[A]): WireProtocol.Decoder[A] = { ab => p.decode(ab) }
}
