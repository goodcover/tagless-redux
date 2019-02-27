package cats.tagless

import scala.reflect.macros.whitebox
import scala.collection.immutable.Seq
import cats.implicits._

class Utils[C <: whitebox.Context](val c: C) { self =>
  import c.universe._

  def process(tre: Tree, autoDerivation: Boolean): U = tre match {
    case q"$mods trait $name[..$tparams] extends ..$parents { $self => ..$body }" =>
      val effectType = extractF(tparams, higherKinded = true)
        .getOrElse(c.abort(c.enclosingPosition, "Expected to find an effect type."))
      new U(mods, name, tparams, body, effectType, autoDerivation)
    case _ => c.abort(c.enclosingPosition, s"Expecting a trait, got a ${showCode(tre)}")
  }

  def extractF(tpes: List[TypeDef], higherKinded: Boolean): Option[TypeDef] = {
    if (higherKinded)
      tpes.collectFirst {
        case tp if tp.tparams.nonEmpty => tp
      } else tpes.lastOption
  }

  class U(
    mods: Modifiers,
    name: TypeName,
    tparams: List[TypeDef],
    body: Seq[Tree],
    effectType: TypeDef,
    autoDerivation: Boolean) {

    lazy val className = name

    lazy val effectTypeArg: TypeName = effectType.name

    lazy val effectTypeName: String = effectType.name.decodedName.toString

    def tArgs(effTpeName: TypeName = effectTypeArg): Seq[TypeName] =
      tparams.map {
        case `effectType` => effTpeName
        case tp           => tp.name
      }

    def tArgs(effTpeName: String): Seq[TypeName] = tArgs(TypeName(effTpeName))

    lazy val abstractTypeMembers: Seq[TypeDef] = {
      body.collect {
        case t: TypeDef => t
      }
    }

    def newTypeMember(definedAt: TermName): Seq[TypeDef] = {
      abstractTypeMembers.map {
        case q"type $t  >: $lowBound <: $upBound" =>
          q"type $t = $definedAt.$t"
        case q"type $t[..$tparams] >: $lowBound <: $upBound" =>
          q"type $t[..$tparams] = $definedAt.$t[..${tparams.map(tp => tp.name)}]"
      }
    }

    /**
      * new type signature with a different effect type name
      * @param newEffectTypeName
      */
    def newTypeSig(newEffectTypeName: String = effectTypeName): Tree =
      tq"$name[..${tArgs(newEffectTypeName)}]"

    def newInstance(typeMembers: Seq[TypeDef]): Tree =
      q"""new ${TypeName(name.decodedName.toString)}[..${tArgs("G")}] {
          ..$typeMembers
          ..${covariantKMethods(from)}
       }"""
    lazy val instanceMapKDef: Tree = {
      q"""
            def mapK[F[_], G[_]]($from: ${newTypeSig("F")})(fk: _root_.cats.~>[F, G]): ${newTypeSig("G")} =
              ${newInstance(newTypeMember(from))}
          """
    }

    lazy val typeLambdaVaryingHigherKindedEffect =
      tq"({type λ[Ƒ[_]] = ${newTypeSig("Ƒ")}})#λ"
    lazy val typeLambdaVaryingHigherKindedEffectFullyRefined =
      tq"({type λ[Ƒ[_]] = ${fullyRefinedTypeSig("Ƒ")}})#λ"

    lazy val typeLambdaVaryingEffect =
      tq"({type λ[T] = $name[..${tArgs("T")}]})#λ"

    lazy val instanceDef: List[Tree] = {
      List(
        companionMapKDef,
        q"""
        implicit def ${TermName("functorKFor" + name.decodedName.toString)}[..$extraTParams]: _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffect] =
          new _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffect] {
            $instanceMapKDef
          }
      """
      )
    }

    lazy val companionMapKDef: Tree = {
      q"""
        def mapK[F[_], G[_], ..$extraTParams]($from: ${newTypeSig("F")})(fk: _root_.cats.~>[F, G]): ${dependentRefinedTypeSig(
        "G",
        from
      )} =
          ${newInstance(newTypeMember(from))}
      """
    }

    lazy val extraTParams: Seq[TypeDef] = tparams.filterNot(Set(effectType))

    lazy val newTypeMemberFullyRefined: Seq[TypeDef] = {
      abstractTypeMembers.map { td =>
        val typeDefBody =
          if (td.tparams.nonEmpty)
            tq"${TypeName(td.name.decodedName.toString + "0")}[..${td.tparams
              .map(tp => tp.name)}]"
          else
            tq"${TypeName(td.name.decodedName.toString + "0")}"
        TypeDef(td.mods, td.name, td.tparams, typeDefBody)
      }
    }

    lazy val fullyRefinedTParams: Seq[TypeDef] = extraTParams ++ refinedTParams

    lazy val refinedTParams: Seq[TypeDef] =
      newTypeMemberFullyRefined.map { defn =>
        val (n, s) = if (defn.tparams.nonEmpty) {
          val tq"${TypeName(name)}[..$tparams]" = defn
          (name, tparams.size)
        } else {
          val tq"${TypeName(name)}" = defn
          (name, 0)
        }

        typeParam(n, s)
      }

    def fullyRefinedTypeSig(newEffectTypeName: String = effectTypeName): Tree =
      refinedTypeSig(newEffectTypeName, newTypeMemberFullyRefined)

    def dependentRefinedTypeSig(newEffectTypeName: String = effectTypeName, dependent: TermName): Tree =
      refinedTypeSig(newEffectTypeName, newTypeMember(dependent))

    def refinedTypeSig(newEffectTypeName: String, refinedTypes: Seq[TypeDef]): Tree = {
      if (abstractTypeMembers.isEmpty)
        newTypeSig(newEffectTypeName)
      else
        tq"$name[..${tArgs(newEffectTypeName)}] { ..$refinedTypes } "

    }

    def covariantTransform(resultType: Tree, originImpl: Tree): (Tree, Tree) = {
      resultType match {
        case tq"${Ident(`effectTypeArg`)}[$resultTpeArg]" =>
          (tq"G[$resultTpeArg]", q"fk($originImpl)")
        case _ => (resultType, originImpl)
      }
    }

    def arguments(params: Seq[ValDef]): Seq[TermName] =
      params.map(p => p.name)

    def covariantKMethods(from: TermName): Seq[Tree] =
      body.collect {
        case q"def $methodName[..$mTParams](..$params): $resultType" =>
          val (newResultType, newImpl) =
            covariantTransform(resultType, q"$from.$methodName(..${arguments(params)})")
          q"""def $methodName[..$mTParams](..$params): $newResultType = $newImpl"""
        case q"def $methodName[..$mTParams](..$params)(..$params2): $resultType" =>
          val (newResultType, newImpl) =
            covariantTransform(resultType, q"$from.$methodName(..${arguments(params)})(..${arguments(params2)})")
          q"""def $methodName[..$mTParams](..$params)(..$params2): $newResultType = $newImpl"""
      } //++ defWithoutParams

    def autoDerivationDef: Tree =
      q"""
      object autoDerive {
        @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
        implicit def fromFunctorK[${effectType}, G[_], ..${extraTParams}](
          implicit fk: _root_.cats.~>[${effectTypeArg}, G],
          FK: _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffect],
          af: ${newTypeSig("F")})
          : ${newTypeSig("G")} = FK.mapK(af)(fk)
      }"""

    lazy val instanceDefFullyRefined: Seq[Tree] = {
      Seq(q"""
       object fullyRefined {
         implicit def ${TermName("functorKForFullyRefined" + name.decodedName.toString)}[..${fullyRefinedTParams}]: _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffectFullyRefined] =
           new _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffectFullyRefined] {
             def mapK[F[_], G[_]]($from: ${fullyRefinedTypeSig("F")})(fk: _root_.cats.~>[F, G]):${fullyRefinedTypeSig(
        "G"
      )} =
                ${newInstance(newTypeMemberFullyRefined)}
         }
         object autoDerive {
           @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
           implicit def fromFunctorK[${effectType}, G[_], ..${fullyRefinedTParams}](
             implicit fk: _root_.cats.~>[${effectTypeArg}, G],
             FK: _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffectFullyRefined],
             af: ${fullyRefinedTypeSig()})
             : ${fullyRefinedTypeSig("G")} = FK.mapK(af)(fk)
           }
       }
      """)
    }

    lazy val newDef: List[Tree] = instanceDef ++ {
      if (autoDerivation)
        List(autoDerivationDef)
      else Nil
    } ++ instanceDefFullyRefined

    val applyInstanceDef = List(
      q"def apply[..${tparams}](implicit inst: $name[..${tArgs()}]): $name[..${tArgs()}] = inst"
    )

  }

  private val from: TermName = TermName("af")
  private val paramFlag      = Modifiers(Flag.PARAM)

  def typeParam(name: String, numOfTParams: Int = 1): TypeDef = {
    val tparams =
      Range(0, numOfTParams).toList
        .as(TypeDef(paramFlag, typeNames.WILDCARD, Nil, TypeBoundsTree(EmptyTree, EmptyTree)))
    TypeDef(paramFlag, TypeName(name), tparams, TypeBoundsTree(EmptyTree, EmptyTree))
  }

}
