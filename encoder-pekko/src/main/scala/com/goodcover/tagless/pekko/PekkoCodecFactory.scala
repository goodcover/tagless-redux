package com.goodcover.tagless.pekko

import com.goodcover.tagless.util.{ CodecFactory, WireProtocol }

trait PekkoCodecFactory extends CodecFactory[PekkoImpl] {
  override def encode[A](implicit p: PekkoImpl[A]): WireProtocol.Encoder[A] = { (a: A) => p.encode(a) }

  override def decode[A](implicit p: PekkoImpl[A]): WireProtocol.Decoder[A] = { ab => p.decode(ab) }
}

object PekkoCodecFactory extends PekkoCodecFactory {
  override def encode[A](implicit p: PekkoImpl[A]): WireProtocol.Encoder[A] = { (a: A) => p.encode(a) }

  override def decode[A](implicit p: PekkoImpl[A]): WireProtocol.Decoder[A] = { ab => p.decode(ab) }
}
