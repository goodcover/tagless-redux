package com.dispalt.tagless.pekko

import cats.tagless.FunctorK
import com.dispalt.tagless.*
import cats.~>
import com.dispalt.tagless.util.{ PairE, Result, WireProtocol }
import com.dispalt.taglessPekko.{ PekkoCodecFactory, PekkoImpl }
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
  )(using q: Quotes): Expr[WireProtocol[Alg]] =
    import q.reflect.*
    val result = '{
      new WireProtocol[Alg]:
        override def encoder: Alg[[X] =>> WireProtocol.Encoded[X]] = ${ deriveEncoder[Alg](system) }
        override def decoder: WireProtocol.Decoder[PairE[
          WireProtocol.Invocation[Alg, *],
          WireProtocol.Encoder
        ]] =
          ${ deriveDecoder[Alg](system) }
    }

    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      report.info("macro output: " + result.show, Position.ofMacroExpansion)
    }
    result

  private def getClassTag[T](using Type[T], Quotes): Expr[ClassTag[T]] = {
    import quotes.reflect.*

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

  private def pekkoImpl[T](using Type[T], Quotes): Expr[PekkoImpl[T]] = {
    import quotes.reflect.*
    Expr.summon[PekkoImpl[T]] match {
      case Some(ct) =>
        ct
      case None     =>
        report.error(
          s"Unable to find a PekkoImpl for type ${Type.show[T]}",
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
        case Nil                =>
          // No arguments - use Unit
          '{ () }
        case List(Nil)          =>
          // Single empty parameter list - use Unit
          '{ () }
        case List(args)         =>
          // Multiple arguments in single parameter list - create tuple
          val argExprs = args.map(_.asExpr)
          Expr.ofTupleFromSeq(argExprs)
        case multipleParamLists =>
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
      val name = Expr(method.name)
      val ff   = method.returnTpt.tpe.show

      val tuples    = convert(argss)
      val tupleArgs = dm.convertArgsToTupleType(method).asType match {
        case '[tupleT] =>
          // Create a dummy expression of the tuple type for serialization
          '{ $tuples.asInstanceOf[tupleT] }
      }
      method.returnTpt.tpe.typeArgs.last.typeArgs.last.asType match {

        case '[result] =>
          val impl = pekkoImpl[result]
          val t    = '{
            given org.apache.pekko.actor.ActorSystem = $system
            (
              PekkoCodecFactory.encode
                .apply(Result($name, ${ tupleArgs })),
              PekkoCodecFactory.decode[result](using $impl)
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
    result

  def deriveDecoder[Alg[_[_]]: Type](system: Expr[org.apache.pekko.actor.ActorSystem])(using
    q: Quotes
  ): Expr[WireProtocol.Decoder[PairE[
    WireProtocol.Invocation[Alg, *],
    WireProtocol.Encoder
  ]]] =
    import q.reflect.*

    given dm: DeriveMacros[q.type] = new DeriveMacros

    val algTypeRepr    = TypeRepr.of[Alg]
    val algSym: Symbol = algTypeRepr.typeSymbol

    def mkDecode(method: DefDef)(
      argss: List[List[Tree]]
    ): Option[(String, Expr[Result[Any] => PairE[WireProtocol.Invocation[Alg, *], WireProtocol.Encoder]])] =
      given dm: DeriveMacros[q.type] = new DeriveMacros

      def rec[A: Type]: List[Type[?]] = Type.of[A] match
        case '[field *: fields] =>
          Type.of[field] :: rec[fields]
        case '[EmptyTuple]      =>
          Nil
        case _                  =>
          quotes.reflect.report.errorAndAbort("Expected known tuple but got: " + Type.show[A])

      method.returnTpt.tpe.typeArgs.last.asType match {

        case '[result] =>
          val impl       = pekkoImpl[Tuple2[String, Any]]
          val implResult = pekkoImpl[result]

          val sym        = method.symbol
          val methodName = method.name

          Some(method.name -> '{
            import WireProtocol.*

            { (result: Result[Any]) =>

              val invocation = new Invocation[Alg, result] {
                override def run[F[_]](mf: Alg[F]): F[result] =
                  // Generate the method call dynamically
                  ${
                    // Create the method call based on the number of parameters
                    val mfTerm    = '{ mf }.asTerm
                    // Get the statically typed tuple from argss
                    val tupleType = dm.convertArgsToTupleType(method)

                    val methodCall = argss match {
                      case Nil =>
                        // No parameters - call method directly: mf.methodName
                        Select(mfTerm, sym).asExprOf[F[result]]

                      case List(Nil) =>
                        // No parameters - call method directly: mf.methodName
                        Apply(Select(mfTerm, sym), Nil).asExprOf[F[result]]

                      case List(params) =>
                        // Multiple parameters - extract from statically typed tuple
                        tupleType.asType match {
                          case '[tupleT] =>
                            '{
                              val args = result.a.asInstanceOf[tupleT]
                              ${
                                val argTerms = (1 to params.length).map { i =>
                                  val fieldName = s"_$i"
                                  Select.unique('{ args }.asTerm, fieldName)
                                }.toList
                                val re       = Apply(Select(mfTerm, sym), argTerms).asExprOf[F[result]]
                                re
                              }
                            }
                        }
                      case _            =>
                        // Multiple parameter lists - access nested tuples
                        tupleType.asType match {
                          case '[tupleT] =>
                            '{
                              val args = result.a.asInstanceOf[tupleT]
                              ${
                                // Generate nested tuple access for each parameter list
                                val paramListArgs = argss.zipWithIndex.map { case (paramList, paramListIndex) =>
                                  val paramListFieldName = s"_${paramListIndex + 1}"
                                  val paramListTerm      = Select.unique('{ args }.asTerm, paramListFieldName)

                                  if (paramList.isEmpty) {
                                    // Empty parameter list - no arguments to extract
                                    Nil
                                  } else if (paramList.length == 1) {
                                    // Single parameter - the term itself is the argument
                                    List(paramListTerm)
                                  } else {
                                    // Multiple parameters - extract from nested tuple
                                    (1 to paramList.length).map { paramIndex =>
                                      val paramFieldName = s"_$paramIndex"
                                      Select.unique(paramListTerm, paramFieldName)
                                    }.toList
                                  }
                                }

                                // Apply arguments using multiple Apply calls for multiple parameter lists
                                val methodCall = paramListArgs.foldLeft(Select(mfTerm, sym): Term) { (acc, args) =>
                                  Apply(acc, args)
                                }
                                methodCall.asExprOf[F[result]]
                              }
                            }
                        }
                    }
                    methodCall
                  }
              }

              PairE(
                invocation,
                PekkoCodecFactory.encode[result](using $implResult)
              )
            }
          })
      }

    def transformVal(value: ValDef): Option[Term] =
      None

    val members: List[(String, Expr[Result[Any] => PairE[WireProtocol.Invocation[Alg, *], WireProtocol.Encoder]])] =
      algSym.declarations
        .filterNot(_.isClassConstructor)
        .flatMap: member =>
          member.tree match
            case method: DefDef => mkDecode(method)(method.paramss.map(_.params))
            case _              => None

    // You can now work with the members
    val mapGenerated = '{
      Map(${
        Expr.ofSeq(members.map { case (key, valueExpr) =>
          '{ (${ Expr(key) }, $valueExpr) }
        })
      }*)
    }

    val result = '{
      new WireProtocol.Decoder[PairE[
        WireProtocol.Invocation[Alg, *],
        WireProtocol.Encoder
      ]]:

        private val localMap = $mapGenerated

        given org.apache.pekko.actor.ActorSystem = $system

        override def apply(ab: Array[Byte]): scala.util.Try[PairE[
          WireProtocol.Invocation[Alg, *],
          WireProtocol.Encoder
        ]] =
          PekkoCodecFactory.decode[Result[Any]].apply(ab).map { result =>
            localMap.getOrElse(result.method, throw new Exception(s"Unknown method: ${result.method}")).apply(result)
          }
    }
    result
