package cats.tagless

import scala.reflect.macros.{blackbox}
import cats.implicits._

class Utils[C <: blackbox.Context](val c: C) { self =>
  import c.universe._

  private[this] case class Member(
    name: TermName,
    decodedName: String,
    tparams: List[Symbol],
    params: List[List[Symbol]],
    tpe: Type,
    abstractType: Boolean)
  private[this] object Member {
    final def fromSymbol(tpe: Type)(sym: Symbol): Member = {
      val memberName = sym.name
      val memberDecl = tpe.decl(memberName)
      sym.isType

      val (tparams, args, memberTpe) = if (sym.isType) {
        (List.empty, List.empty, sym.asType.toType)
      } else {
        val meth = memberDecl.asMethod
        (meth.typeParams, meth.paramLists, meth.returnType.asSeenFrom(tpe, tpe.typeSymbol))
      }
      //
      Member(memberName.toTermName, memberName.decodedName.toString, tparams, args, memberTpe, sym.isType)
    }
  }

  def processAnnotation(tre: Tree, autoDerivation: Boolean): U = tre match {
    case q"$mods trait $name[..$tparams] extends ..$parents { $self => ..$body }" =>
      val effectType = extractF(tparams, higherKinded = true)
        .getOrElse(c.abort(c.enclosingPosition, "Expected to find an effect type."))
      new U(name, tparams, body, effectType, autoDerivation)
    case _ => c.abort(c.enclosingPosition, s"Expecting a trait, got a ${showCode(tre)}")
  }

  def extractF(tpes: List[TypeDef], higherKinded: Boolean): Option[TypeDef] = {
    if (higherKinded)
      tpes.collectFirst {
        case tp if tp.tparams.nonEmpty => tp
      } else tpes.lastOption
  }

  def extractFT(tpes: List[Type], higherKinded: Boolean): Option[Type] = {
    if (higherKinded)
      tpes.collectFirst {
        case tp if tp.typeParams.nonEmpty => tp
      } else tpes.lastOption
  }

  def processVal(name: TypeName, sym: Symbol): U2 = {
    val tpe = sym.asClass.selfType
    val r = tpe.decls
      .collect {
        case sym => Member.fromSymbol(tpe)(sym)
      }
      .toSeq

    new U2(
      name,
      tpe.typeArgs,
      r,
      extractFT(tpe.typeArgs, true).getOrElse(c.abort(c.enclosingPosition, "Expected to find a higher kinded type.")),
      false
    )
  }

  class U(name: TypeName, tparams: List[TypeDef], body: Seq[Tree], effectType: TypeDef, autoDerivation: Boolean) {

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
          _root_.cats.tagless.Derive.functorK[$typeLambdaVaryingHigherKindedEffect]
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

    lazy val defWithoutParams: Seq[Tree] =
      body.collect {
        case q"def $methodName[..$mTParams]: $resultType" =>
          val (newResultType, newImpl) = covariantTransform(resultType, q"$from.$methodName")
          q"""def $methodName[..$mTParams]: $newResultType = $newImpl"""
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
      } ++ defWithoutParams

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

    lazy val newDef: List[Tree] = instanceDef /*++ {
      if (autoDerivation)
        List(autoDerivationDef)
      else Nil
    } ++ instanceDefFullyRefined*/

    val applyInstanceDef = List(
      q"def apply[..${tparams}](implicit inst: $name[..${tArgs()}]): $name[..${tArgs()}] = inst"
    )

  }

  class U2(name: TypeName, tparams: List[Type], body: Seq[Member], effectType: Type, autoDerivation: Boolean) {

    lazy val effectTypeArg: TypeName = effectType.typeSymbol.name.toTypeName

    lazy val effectTypeName: String = effectType.typeSymbol.name.decodedName.toString

    def tArgs(effTpeName: TypeName = effectTypeArg): Seq[TypeName] =
      tparams.map {
        case `effectType` => effTpeName
        case tp           => tp.typeSymbol.name.toTypeName
      }

    def newTypeSig(newEffectTypeName: String = effectTypeName): Tree =
      tq"$name[..${tArgs(TypeName(newEffectTypeName))}]"

    lazy val typeLambdaVaryingHigherKindedEffect =
      tq"({type λ[Ƒ[_]] = ${newTypeSig("Ƒ")}})#λ"

    lazy val abstractTypeMembers: Seq[Type] = {
      body.collect {
        case Member(_, _, _, _, tpe, true) => tpe
      }
    }

    def newTypeMember(definedAt: TermName): Seq[TypeDef] = {
      abstractTypeMembers.map { t =>
        q"type ${t.typeSymbol.name.toTypeName} = $definedAt.${t.typeSymbol.name.toTypeName}"
      }
    }

    def covariantTransform(resultType: Type, originImpl: Tree): (Tree, Tree) = {
      if (resultType.typeSymbol == effectType.typeSymbol) {
        (tq"G[..${resultType.typeArgs}]", q"fk($originImpl)")
      } else {
        // TODO[Jesus] what the fudge
        (tq"${resultType.typeSymbol.name.decodedName.toTypeName}", originImpl)
      }
    }

    lazy val defWithoutParams: Seq[Tree] =
      body.collect {
        case Member(name, _, tparams, Nil, tpe, false) =>
          val (newResultType, newImpl) = covariantTransform(tpe, q"$from.$name")
          q"""def $name[..${tparams.map(_.asType.toType)}]: $newResultType = $newImpl"""
      }

    def arguments(params: List[List[Symbol]]): List[List[TermName]] =
      params.map(_.map(_.name.toTermName))

    def covariantKMethods(from: TermName): Seq[Tree] =
      body.collect {
        case Member(name, _, tparams, params, tpe, false) =>
          val (newResultType, newImpl) =
            covariantTransform(tpe, q"$from.$name(...${arguments(params)})")
          val theParams = params.map(_.map(f => q"val ${f.name.toTermName}: ${f.asTerm.typeSignature}"))
          q"""def $name[..${tparams.map(_.asType.toType)}](...${theParams}): $newResultType = $newImpl"""
      }

    def newInstance(typeMembers: Seq[TypeDef]): Tree =
      q"""new ${TypeName(name.decodedName.toString)}[..${tArgs(TypeName("G"))}] {
          ..$typeMembers
          ..${covariantKMethods(from)}
       }"""

    lazy val instanceMapKDef: Tree = {
      q"""
            def mapK[F[_], G[_]]($from: ${newTypeSig("F")})(fk: _root_.cats.~>[F, G]): ${newTypeSig("G")} =
              ${newInstance(newTypeMember(from))}
          """
    }

    lazy val newFunctorKCls: Tree = {
      q"""new _root_.cats.tagless.FunctorK[$typeLambdaVaryingHigherKindedEffect] {
        $instanceMapKDef
        }"""
    }
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
