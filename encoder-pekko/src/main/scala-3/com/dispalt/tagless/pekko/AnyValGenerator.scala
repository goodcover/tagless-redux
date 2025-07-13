package com.dispalt.tagless.pekko

import com.dispalt.tagless.*
import cats.~>
import com.dispalt.tagless.util.WireProtocol.{ Decoder, Encoder, Invocation }
import com.dispalt.tagless.util.{ PairE, WireProtocol }
import com.dispalt.taglessPekko.DefaultGenerator
import org.apache.pekko.actor.ActorSystem

import scala.annotation.experimental
import scala.quoted.*

trait AnyValGenerator extends DefaultGenerator {}

@experimental
object MacroPekkoWireProtocol:
  inline def derive[Alg[_[_]]]: WireProtocol[Alg] = ${ wireProtocol(system = '{ system }) }

  def wireProtocol[Alg[_[_]]: Type](using q: Quotes, system: Expr[ActorSystem]): Expr[WireProtocol[Alg]] = '{
    new WireProtocol[Alg]:
      override def encoder: Alg[WireProtocol.Encoded]                   = ${ deriveEncoder }
      override def decoder: Decoder[PairE[Invocation[Alg, *], Encoder]] = ???

  }

  private[tagless] def deriveEncoder[Alg[_[_]]: Type](using
    q: Quotes,
    system: Expr[ActorSystem]
  ): Expr[Alg[WireProtocol.Encoded]] =
    import quotes.reflect.*
    given DeriveMacros[q.type] = new DeriveMacros

    val F = TypeRepr.of[F]
    val G = TypeRepr.of[G]
