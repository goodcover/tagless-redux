package com.dispalt.taglessAkka

import com.dispalt.tagless.util.{CodecFactory, WireProtocol}

object AkkaCodecFactory extends CodecFactory[AkkaImpl] {
  override def encode[A](implicit p: AkkaImpl[A]): WireProtocol.Encoder[A] = { (a: A) =>
    p.encode(a)
  }

  override def decode[A](implicit p: AkkaImpl[A]): WireProtocol.Decoder[A] = { ab =>
    p.decode(ab)
  }
}
