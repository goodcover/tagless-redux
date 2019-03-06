package com.dispalt.tagless

import com.dispalt.tagless.util.CodecFactory

import scala.reflect.macros.whitebox

abstract class EncoderGeneratorMacro {

  val c: whitebox.Context

  import c.universe._

  val pkg      = q"_root_.com.dispalt.tagless.util"
  val wireP    = q"$pkg.WireProtocol"
  val wirePTpe = tq"_root_.com.dispalt.tagless.util.WireProtocol"

  // (wildcard?, output)
  def toType(arg: Tree): (Boolean, Tree) = arg match {
    // God this is awful
    case AppliedTypeTree(Select(_, TypeName("<repeated>")), args) =>
      (true, tq"Seq[..$args]")
    case x => (false, x)
  }

  def encoderBody(traitStats: Seq[Tree], theF: TypeName, encodeFn: Symbol, decodeFn: Symbol): Seq[Tree] = {
    traitStats.map {
      case q"def ${name: TermName}[..$tps](..${params: List[ValDef]}): ${Ident(someF)}[$out]" if someF == theF =>
        val tupleTpeBase = TermName(s"Tuple${params.size}")
        q"""
        final def $name[..$tps](..$params): $wireP.Encoded[$out] = (
          $encodeFn((${name.decodedName.toString}, $tupleTpeBase(..${params.map(_.name)}))),
          $decodeFn[$out]
        )
        """
      case q"def ${name: TermName}: ${Ident(someF)}[$out]" if someF == theF =>
        q"""
        final val ${name}: $wireP.Encoded[$out] = (
          $encodeFn((${name.decodedName.toString}, ())),
          $decodeFn[$out]
        )"""
      case other =>
        c.abort(c.enclosingPosition, s"Illegal method [$other]")
    }
  }

  def decoderCases(
    traitStats: Seq[Tree],
    theF: TypeName,
    unifiedBase: Tree,
    argName: TermName,
    encodeFn: Symbol,
    decodeFn: Symbol
  ): Seq[Tree] = {
    val cases = traitStats.map {
      case q"def ${name: TermName}[..$tps](..${params: List[ValDef]}): ${Ident(someF)}[$out]" if someF == theF =>
        val typeInTypeOut = params.map { p =>
          toType(p.tpt)
        }.toVector

        val arglist = (1 to params.size).map(i => (i, s"_$i")).map {
          case (i, x) =>
            val wildcard = typeInTypeOut(i - 1)._1
            val term     = TermName(x)
            if (wildcard) {
              q"(args.$term: _*)"
            } else {
              q"args.$term"
            }

        }
        val tupleTpeBase = TypeName(s"Tuple${params.size}")
        val nameLit      = name.decodedName.toString

        cq"""$nameLit =>
            val args = $argName.asInstanceOf[$tupleTpeBase[..${typeInTypeOut.map(_._2)}]]
            val invocation = new $wireP.Invocation[$unifiedBase, $out] {
              final override def run[F[_]](mf: $unifiedBase[F]): F[$out] = mf.$name(..$arglist)
              final override def toString: String = {
                val name = $nameLit
                s"$$name$$args"
              }
            }
            $pkg.PairE(invocation, $encodeFn[$out])
         """
      case q"def ${name: TermName}: ${Ident(someF)}[$out]" if someF == theF =>
        val nameLit = name.decodedName.toString
        cq"""$nameLit =>
                  val invocation = new $wireP.Invocation[$unifiedBase, $out] {
                    final override def run[F[_]](mf: $unifiedBase[F]): F[$out] = mf.$name
                    final override def toString: String = $nameLit
                  }
                  $pkg.PairE(invocation, $encodeFn[$out])
             """
      case other =>
        c.abort(c.enclosingPosition, s"Illegal method [$other]")
    }
    cases :+ cq"""other => throw new IllegalArgumentException(s"Unknown type tag $$other")"""
  }

  def apply[C <: CodecFactory: TypeTag](cf: C, base: ClassDef, companion: Option[ModuleDef]): Tree = {
    val typeName               = base.name
    val traitStats             = base.impl.body
    val (theF, abstractParams) = (base.tparams.last.name, base.tparams.dropRight(1))
    val abstractTypes          = abstractParams.map(_.name)
    val decodeFn               = typeOf[C].decl(TermName("decode"))
    val encodeFn               = typeOf[C].decl(TermName("encode"))

    val unifiedBase: Tree =
      if (abstractTypes.isEmpty) {
        tq"${base.name}"
      } else {
        tq"({type X[F[_]] = $typeName[..$abstractTypes, F]})#X"
      }

    val unifiedInvocation = tq"({type X[A] = $wireP.Invocation[$unifiedBase, A]})#X"

    val instanceName = TermName(s"taglessWireProtocol${typeName.decodedName.toString}")

    val value = TermName("arg")

    val decoderBody = q"""
        final val decoder: $wireP.Decoder[$pkg.PairE[$unifiedInvocation, $wireP.Encoder]] =
          new $wireP.Decoder[$pkg.PairE[$unifiedInvocation, $wireP.Encoder]] {
            final override def apply(bytes: _root_.scala.Array[Byte]) = {
              $decodeFn[(String, Any)].apply(bytes).map {
              case (key, $value) =>
                key match {
                  case ..${decoderCases(traitStats, theF, unifiedBase, value, encodeFn, decodeFn)}
                }
              }

            }
          }
    """

    val companionStats: Seq[Tree] = Seq(q"""
        implicit def $instanceName[..$abstractParams]: $wirePTpe[$unifiedBase]  =
         new $wirePTpe[$unifiedBase] {

            final val encoder = new ${typeName}[..$abstractTypes, $wireP.Encoded] {
              ..${encoderBody(traitStats, theF, encodeFn, decodeFn)}
            }

            $decoderBody
          }
    """)

    val newCompanion = companion match {
      case Some(q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }") =>
        q"""$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
          ..$objDefs
          ..$companionStats
        }"""
      case None =>
        q"object ${base.name.toTermName} { ..$companionStats }"

    }
    val classCompanion = List(base, newCompanion)
    val completeResult = Block(classCompanion, Literal(Constant(())))
    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      println(showCode(completeResult))
    }
    completeResult
  }
}
