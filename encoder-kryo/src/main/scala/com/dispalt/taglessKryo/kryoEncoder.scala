package com.dispalt.taglessKryo

import com.dispalt.tagless.EncoderGeneratorMacro

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.whitebox

@compileTimeOnly("Cannot expand @kryoEncoder")
class kryoEncoder extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro EncoderGenerator.kryoImpl
}

class EncoderGenerator(val c: whitebox.Context) extends EncoderGeneratorMacro {

  def kryoImpl(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val i = q"import _root_.com.dispalt.taglessKryo.Default._"
    annottees.map(_.tree).toList match {
      case Nil                     => c.abort(c.enclosingPosition, "Unexpected error")
      case (head: ClassDef) :: Nil => c.Expr[Any](apply[KryoCodec.type, KryoImpl](KryoCodec, head, None, i, Nil))
      case (classDef: ClassDef) :: (objectDef: ModuleDef) :: Nil =>
        c.Expr[Any](apply[KryoCodec.type, KryoImpl](KryoCodec, classDef, Some(objectDef), i, Nil))
    }
  }
}
