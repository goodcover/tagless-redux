package com.dispalt.tagless.util

import com.dispalt.tagless.util.WireProtocol.{Decoder, Encoder, Invocation}

import scala.util.Try

// move to runtime
trait WireProtocol[M[_[_]]] {
  def decoder: Decoder[PairE[Invocation[M, *], Encoder]]
  def encoder: M[WireProtocol.Encoded]
}

object WireProtocol {

  trait Encoder[A] {
    def apply(a: A): Array[Byte]
  }

  trait Decoder[A] {
    def apply(ab: Array[Byte]): Try[A]
  }

  def apply[M[_[_]]](implicit M: WireProtocol[M]): WireProtocol[M] = M
  type Encoded[A] = (Array[Byte], Decoder[A])

  trait Invocation[M[_[_]], A] {
    def run[F[_]](mf: M[F]): F[A]
  }
}