package com.goodcover.tagless.pekko

import scala.reflect.macros.blackbox.{Context => MacroContext}

trait AnyValGenerator extends DefaultGenerator {
  implicit def pekkoImplMacroAnyVal[A <: AnyVal]: PekkoImpl[A] = macro AnyValGeneratorMacros.impl[A]
}

class AnyValGeneratorMacros(val c: MacroContext) {
  import c.universe._

  private def withAnyValParam[R](tpe: Type)(f: Symbol => R): Option[R] =
    tpe.baseType(c.symbolOf[AnyVal]) match {
      case NoType => None
      case _ =>
        primaryConstructor(tpe).map(_.paramLists.flatten).collect {
          case param :: Nil => f(param)
        }
    }

  private def primaryConstructor(t: Type) =
    t.members.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m.typeSignature.asSeenFrom(t, t.typeSymbol)
    }

  def impl[T <: AnyVal](implicit t: WeakTypeTag[T]): Expr[PekkoImpl[T]] = {
    c.Expr[PekkoImpl[T]](withAnyValParam(t.tpe) { param =>
      q"""
        implicitly[_root_.com.goodcover.tagless.pekko.PekkoImpl[${param.typeSignature}]].imap(new ${t.tpe}(_))((v: ${t.tpe}) => v.${param.name.toTermName})
      """
    }.getOrElse(c.abort(c.enclosingPosition, s"Could find ${t.tpe}")))
  }
}
