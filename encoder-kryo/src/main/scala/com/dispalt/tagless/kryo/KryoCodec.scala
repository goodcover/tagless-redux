package com.dispalt.tagless.kryo

import com.dispalt.tagless.util.CodecFactory
import com.dispalt.tagless.util.WireProtocol.{Decoder, Encoder}
import com.twitter.chill.KryoInjection

object KryoCodec extends CodecFactory {

  def encode[A]: Encoder[A] = { a: A =>
    KryoInjection(a)
  }

  def decode[A]: Decoder[A] = { ab: Array[Byte] =>
    KryoInjection.invert(ab).map { o =>
      o.asInstanceOf[A]
    }
  }

}
