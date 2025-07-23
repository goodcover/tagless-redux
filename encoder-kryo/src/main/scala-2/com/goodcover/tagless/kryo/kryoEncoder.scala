package com.goodcover.tagless.kryo

import com.goodcover.tagless.EncoderGeneratorMacro

import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.reflect.macros.whitebox

@compileTimeOnly("Cannot expand @kryoEncoder")
class kryoEncoder extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro EncoderGenerator.kryoImpl
}

class EncoderGenerator(val c: whitebox.Context) extends EncoderGeneratorMacro {

  def kryoImpl(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    annottees.map(_.tree).toList match {
      case (head: ClassDef) :: Nil                               =>
        c.Expr[Any]( //
          apply[KryoCodec.type, KryoImpl]( //
            head,
            None,
            EmptyTree,
            Nil
          )
        )
      case (classDef: ClassDef) :: (objectDef: ModuleDef) :: Nil =>
        c.Expr[Any]( //
          apply[KryoCodec.type, KryoImpl]( //
            classDef,
            Some(objectDef),
            EmptyTree,
            Nil
          )
        )
      case _                                                     => c.abort(c.enclosingPosition, "Unexpected error")
    }
  }
}
