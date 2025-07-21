package com.dispalt.tagless.util

import com.dispalt.tagless.util.WireProtocol.{ Decoder, Encoder }

trait CodecFactory[P[_]] {

  type Aux[A] = P[A]

  def encode[A](implicit p: P[A]): Encoder[A]
  def decode[A](implicit p: P[A]): Decoder[A]
}
