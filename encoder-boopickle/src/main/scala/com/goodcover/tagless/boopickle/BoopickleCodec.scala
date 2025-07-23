package com.goodcover.tagless.boopickle

import java.nio.ByteBuffer
import boopickle.{ Pickler, UnpickleImpl }
import com.goodcover.tagless.util.CodecFactory
import com.goodcover.tagless.util.WireProtocol.{ Decoder, Encoder }

import scala.util.Try

trait BoopickleCodec extends CodecFactory[Pickler] {
  override def encode[A](implicit p: Pickler[A]): Encoder[A] = encoder

  override def decode[A](implicit p: Pickler[A]): Decoder[A] = decoder

  def encoder[A](implicit pickler: Pickler[A]): Encoder[A] = new Encoder[A] {

    override def apply(value: A): Array[Byte] =
      _root_.boopickle.PickleImpl.intoBytes(value).array()
  }

  def decoder[A](implicit pickler: Pickler[A]): Decoder[A] = new Decoder[A] {

    override def apply(bits: Array[Byte]): Try[A] =
      UnpickleImpl
        .apply[A]
        .tryFromBytes(ByteBuffer.wrap(bits))
  }
}

object BoopickleCodec extends BoopickleCodec
