package com.dispalt.tagless.kryo

import com.dispalt.tagless.util.CodecFactory
import com.dispalt.tagless.util.WireProtocol.{ Decoder, Encoder }

trait KryoCodec extends CodecFactory[KryoImpl] {

  override def encode[A](implicit p: KryoImpl[A]): Encoder[A] = { (a: A) => p.serialize(a).get }

  override def decode[A](implicit p: KryoImpl[A]): Decoder[A] = { (ab: Array[Byte]) => p.deserialize[A](ab) }

}

object KryoCodec extends KryoCodec
