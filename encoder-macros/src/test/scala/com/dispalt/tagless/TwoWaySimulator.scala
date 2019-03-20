package com.dispalt.tagless

import cats.tagless.FunctorK
import cats.{~>, Applicative, Functor}
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.tagless.syntax.functorK._
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.tagless.util.WireProtocol.Decoder

import scala.util.{Failure, Success, Try}

object TwoWaySimulator {

  def server[M[_[_]], F[_]: Applicative](
    actions: M[F]
  )(
    implicit M: WireProtocol[M]
  ): Array[Byte] => F[Try[Array[Byte]]] = { in =>
    M.decoder.apply(in) match {
      case Success(p) =>
        val r: F[p.A] = p.first.run(actions)
        r.map(a => Success(p.second.apply(a)))
      case Failure(cause) =>
        (Failure(cause): Try[Array[Byte]]).pure[F]
    }

  }

  type DecodingResultT[F[_], A] = F[Try[A]]

  def client[M[_[_]], F[_]: Functor](
    server: Array[Byte] => F[Try[Array[Byte]]]
  )(
    implicit M: WireProtocol[M],
    MI: FunctorK[M]
  ): M[DecodingResultT[F, ?]] =
    M.encoder
      .mapK[DecodingResultT[F, ?]](new (WireProtocol.Encoded ~> DecodingResultT[F, ?]) {
        override def apply[A](fa: (Array[Byte], Decoder[A])): F[Try[A]] = {
          server(fa._1).map(_.flatMap(fa._2.apply))
        }
      })
}
