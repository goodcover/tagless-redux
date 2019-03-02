package com.dispalt.tagless.util

import com.dispalt.tagless.util.WireProtocol.{Decoder, Encoder}

trait CodecFactory {
  def encode[A]: Encoder[A]
  def decode[A]: Decoder[A]
}
