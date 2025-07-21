package com.dispalt.tagless.pekko

import com.dispalt.tagless.EncoderGeneratorMacro

import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.reflect.macros.whitebox

@compileTimeOnly("Cannot expand @pekkoEncoder")
class pekkoEncoder extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro EncoderGenerator.pekkoImpl
}

class EncoderGenerator(val c: whitebox.Context) extends EncoderGeneratorMacro {

  def pekkoImpl(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    annottees.map(_.tree).toList match {
      case (head: ClassDef) :: Nil                               =>
        c.Expr[Any](
          apply[PekkoCodecFactory.type, PekkoImpl](
            head,
            None,
            EmptyTree,
            Seq(q"system: _root_.org.apache.pekko.actor.ActorSystem")
          )
        )
      case (classDef: ClassDef) :: (objectDef: ModuleDef) :: Nil =>
        c.Expr[Any](
          apply[PekkoCodecFactory.type, PekkoImpl](
            classDef,
            Some(objectDef),
            EmptyTree,
            Seq(q"system: _root_.org.apache.pekko.actor.ActorSystem")
          )
        )

      case _ => c.abort(c.enclosingPosition, "Unexpected error")
    }
  }
}
