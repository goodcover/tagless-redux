package com.dispalt.tagless.pekko

import cats.tagless.FunctorK
import com.dispalt.tagless.*
import cats.~>
import com.dispalt.tagless.util.{ PairE, Result, WireProtocol }
import com.dispalt.taglessPekko.PekkoImpl
import org.apache.pekko.actor.ActorSystem

import scala.annotation.experimental
import scala.quoted.*
import scala.reflect.ClassTag

trait AnyValGenerator extends com.dispalt.taglessPekko.DefaultGenerator {}

@experimental
object MacroPekkoWireProtocol:
  inline def derive[Alg[_[_]]](using system: ActorSystem): WireProtocol[Alg] = ${
    wireProtocol('{ system })
  }

  def wireProtocol[Alg[_[_]]: Type](
    system: Expr[org.apache.pekko.actor.ActorSystem],
  )(using q: Quotes): Expr[WireProtocol[Alg]] = '{
    new WireProtocol[Alg]:
      override def encoder: Alg[[X] =>> WireProtocol.Encoded[X]] = ${ deriveEncoder[Alg](system) }
      override def decoder: WireProtocol.Decoder[PairE[
        WireProtocol.Invocation[Alg, *],
        WireProtocol.Encoder
      ]] = ${ deriveDecoder[Alg](system) }
  }

  private def getClassTag[T](using Type[T], Quotes): Expr[ClassTag[T]] = {
    import quotes.reflect._

    Expr.summon[ClassTag[T]] match {
      case Some(ct) =>
        ct
      case None     =>
        report.error(
          s"Unable to find a ClassTag for type ${Type.show[T]}",
          Position.ofMacroExpansion
        )
        throw new Exception("Error when applying macro")
    }

  }

  private def genericDeriveEncoder[Alg[_[_]]: Type](system: Expr[ActorSystem])(using
    q: Quotes
  ): Expr[Alg[[X] =>> WireProtocol.Encoded[X]]] =
    import q.reflect.*
    given dm: DeriveMacros[q.type] = new DeriveMacros

    def convert(argss: List[List[Tree]]) = {
      // Convert argss: List[List[Tree]] to a tuple of tuples
      val argsTuple = argss match {
        case Nil                            =>
          // No arguments - use Unit
          '{ () }
        case List(Nil)                      =>
          // Single empty parameter list - use Unit
          '{ () }
        case List(args) if args.length == 1 =>
          // Single argument - just use the argument directly
          args.head.asExpr
        case List(args)                     =>
          // Multiple arguments in single parameter list - create tuple
          val argExprs = args.map(_.asExpr)
          Expr.ofTupleFromSeq(argExprs)
        case multipleParamLists             =>
          // Multiple parameter lists - create tuple of tuples
          val paramListTuples = multipleParamLists.map { paramList =>
            if (paramList.isEmpty) '{ () }
            else if (paramList.length == 1) paramList.head.asExpr
            else Expr.ofTupleFromSeq(paramList.map(_.asExpr))
          }
          Expr.ofTupleFromSeq(paramListTuples)
      }
      argsTuple
    }

    def transformDef(method: DefDef)(argss: List[List[Tree]]): Option[Term] =
      val name      = Expr(method.name)
      val ff        = method.returnTpt.tpe.show
      val tupleArgs = convert(argss)
      method.returnTpt.tpe.typeArgs.last.typeArgs.last.asType match {

        case '[result] =>
          val foo       = Type.show[result]
          val pekkoImpl = Expr.summon[PekkoImpl[result]].getOrElse(throw new Exception("fuuu"))
          val t         = '{
            given org.apache.pekko.actor.ActorSystem = $system
            (
              com.dispalt.taglessPekko.PekkoCodecFactory.encode
                .apply(Result($name, ${ tupleArgs })),
              com.dispalt.taglessPekko.PekkoCodecFactory.decode[result](using $pekkoImpl)
            )
          }.asTerm
          Some(t)
      }

    def transformVal(value: ValDef): Option[Term] =
      None

    None.newClassOf[Alg[WireProtocol.Encoded]](transformDef, transformVal)

  def deriveEncoder[Alg[_[_]]: Type](system: Expr[org.apache.pekko.actor.ActorSystem])(using
    Type[WireProtocol.Encoded[*]]
  )(using
    Quotes
  ): Expr[Alg[[X] =>> com.dispalt.tagless.util.WireProtocol.Encoded[X]]] =
    import quotes.reflect.*

    val algTypeRepr = TypeRepr.of[Alg]
    val algSym      = algTypeRepr.typeSymbol

    // Generate implementation based on trait name with enhanced analysis
    val result = genericDeriveEncoder(system)
    report.warning(result.show)
    //
    result

  def deriveDecoder[Alg[_[_]]: Type](system: Expr[org.apache.pekko.actor.ActorSystem])(using
    q: Quotes
  ): Expr[WireProtocol.Decoder[PairE[
    WireProtocol.Invocation[Alg, *],
    WireProtocol.Encoder
  ]]] =
    import quotes.reflect.*
    given dm: DeriveMacros[q.type] = new DeriveMacros

    val algTypeRepr = TypeRepr.of[Alg]
    val algSym      = algTypeRepr.typeSymbol

    algTypeRepr.members

    // Generate implementation based on trait name with enhanced analysis
    def transformDef(method: DefDef)(argss: List[List[Tree]]): Option[Term] =
      val name = Expr(method.name)
      val ff   = method.returnTpt.tpe.show
      Some('{})

    def transformVal(value: ValDef): Option[Term] =
      None

    None.newClassOf[WireProtocol.Decoder[PairE[
      WireProtocol.Invocation[Alg, *],
      WireProtocol.Encoder
    ]]](transformDef, transformVal)
