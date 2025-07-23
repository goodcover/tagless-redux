package com.goodcover.tagless

import scala.annotation.experimental
import scala.quoted.*
import com.goodcover.tagless.*
import com.goodcover.tagless.util.{ CodecFactory, PairE, Result, WireProtocol }

@experimental
object WireProtocolKryoLike {

  def wireProtocol[Alg[_[_]]: Type, P[_]: Type, C <: CodecFactory[P]: Type](
    system: Expr[C],
  )(using q: Quotes): Expr[WireProtocol[Alg]] =
    import q.reflect.*
    val result = '{
      new WireProtocol[Alg]:
        override def encoder: Alg[[X] =>> WireProtocol.Encoded[X]] = ${ deriveEncoder[Alg, P, C](system) }

        override def decoder: WireProtocol.Decoder[PairE[
          WireProtocol.Invocation[Alg, *],
          WireProtocol.Encoder
        ]] =
          ${ deriveDecoder[Alg, P, C](system) }
    }

    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      report.info("macro output: " + result.show, Position.ofMacroExpansion)
    }
    result

  private def genericDeriveEncoder[Alg[_[_]]: Type, P[_]: Type, C <: CodecFactory[P]: Type](system: Expr[C])(using
    q: Quotes
  ): Expr[Alg[[X] =>> WireProtocol.Encoded[X]]] =
    import q.reflect.*
    given dm: DeriveMacros[q.type] = new DeriveMacros

    def transformDef(method: DefDef)(argss: List[List[Tree]]): Option[Term] =
      val name = Expr(method.name)
      val ff   = method.returnTpt.tpe.show

      val tuples = dm.convertTuples(argss)
      val ttype  = dm.convertArgsToTupleType(method).asType
      ttype match {
        case '[tupleT] =>
          // Create a dummy expression of the tuple type for serialization
          val tupleArgs = '{ $tuples.asInstanceOf[tupleT] }

          method.returnTpt.tpe.typeArgs.last.typeArgs.last.asType match {

            case '[result] =>
              val implResult = dm.summonP[result, P]         // .asInstanceOf[Expr[system.Aux[result]]]
              val implTuple  = dm.summonP[Result[tupleT], P] // .asInstanceOf[Expr[system.Aux[Result[tupleT]]]]
              val t          = '{
                (
                  $system
                    .encode[Result[tupleT]](using $implTuple)
                    .apply(Result($name, ${ tupleArgs })),
                  $system.decode[result](using $implResult)
                )
              }.asTerm
              Some(t)
          }
      }

    def transformVal(value: ValDef): Option[Term] =
      None

    None.newClassOf[Alg[WireProtocol.Encoded]](transformDef, transformVal)

  def deriveEncoder[Alg[_[_]]: Type, P[_]: Type, C <: CodecFactory[P]: Type](system: Expr[C])(using
    Type[WireProtocol.Encoded[*]]
  )(using
    Quotes
  ): Expr[Alg[[X] =>> com.goodcover.tagless.util.WireProtocol.Encoded[X]]] =
    import quotes.reflect.*

    val algTypeRepr = TypeRepr.of[Alg]
    val algSym      = algTypeRepr.typeSymbol

    // Generate implementation based on trait name with enhanced analysis
    val result = genericDeriveEncoder[Alg, P, C](system)
    result

  def deriveDecoder[Alg[_[_]]: Type, P[_]: Type, C <: CodecFactory[P]: Type](system: Expr[C])(using
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
          val impl       = dm.summonP[Tuple2[String, Any], P]
          val implResult = dm.summonP[result, P]

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
                $system.encode[result](using $implResult)
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

    val implResult = dm.summonP[Result[Any], P] // .asInstanceOf[Expr[system.Aux[result]]]

    val result = '{
      new WireProtocol.Decoder[PairE[
        WireProtocol.Invocation[Alg, *],
        WireProtocol.Encoder
      ]]:

        private val localMap = $mapGenerated

        override def apply(ab: Array[Byte]): scala.util.Try[PairE[
          WireProtocol.Invocation[Alg, *],
          WireProtocol.Encoder
        ]] =
          $system.decode[Result[Any]](using $implResult).apply(ab).map { result =>
            localMap.getOrElse(result.method, throw new Exception(s"Unknown method: ${result.method}")).apply(result)
          }
    }
    result

}
