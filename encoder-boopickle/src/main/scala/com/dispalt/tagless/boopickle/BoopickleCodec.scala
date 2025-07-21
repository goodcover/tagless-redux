package com.dispalt.tagless.boopickle

import java.nio.ByteBuffer

import boopickle.Pickler
import boopickle.Default._
import com.dispalt.tagless.util.WireProtocol.{Decoder, Encoder}

import scala.util.Try

object BoopickleCodec {

  def encoder[A](implicit pickler: Pickler[A]): Encoder[A] = new Encoder[A] {

    override def apply(value: A): Array[Byte] =
      Pickle.intoBytes(value).array()
  }

  def decoder[A](implicit pickler: Pickler[A]): Decoder[A] = new Decoder[A] {

    override def apply(bits: Array[Byte]): Try[A] =
      Unpickle
        .apply[A]
        .tryFromBytes(ByteBuffer.wrap(bits))
  }
}
