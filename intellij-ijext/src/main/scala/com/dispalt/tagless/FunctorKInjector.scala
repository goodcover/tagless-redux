package com.dispalt.tagless

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class FunctorKInjector extends SyntheticMembersInjector {
  import FunctorKInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = true

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case aClass: ScTypeDefinition =>
            mkFinalAlg(aClass) ++
              mkFunctorK(aClass) ++
              mkWireProtocolKryo(aClass) ++
              mkWireProtocolPekko(aClass) ++
              mkInstrument(aClass)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}

object FunctorKInjector {
  private[this] val autoFuncAnn    = "cats.tagless.autoFunctorK"
  private[this] val instrumentAnn  = "cats.tagless.autoInstrument"
  private[this] val finalAlgAnn    = "cats.tagless.finalAlg"
  private[this] val kryoEncoderAnn = "com.dispalt.taglessKryo.kryoEncoder"
  private[this] val pekkoEncoderAnn = "com.dispalt.taglessPekko.pekkoEncoder"

  private def isAutoFunctorK(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(autoFuncAnn) != null

  private def isInstrument(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(instrumentAnn) != null

  private def isKryoEncoder(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(kryoEncoderAnn) != null

  private def isFinalAlg(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(finalAlgAnn) != null

  private def isPekkoEncoder(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases(pekkoEncoderAnn) != null

  /** (tpNames, returnType) */
  private def typeParams(clazz: ScTypeDefinition) = {
    val effectParam = clazz.typeParameters.collectFirst {
      case t if t.typeParameters.nonEmpty => t
    }

    effectParam.map { effP =>
      // Don't know how to compute

      val tpName = clazz.typeParameters.filterNot(_ eq effP) match {
        case Seq() => ""
        case x     => x.map(_.name).mkString("[", ",", "]")
      }

      val tpText = clazz.typeParameters.filterNot(_ eq effP) match {
        case Seq() => s"${clazz.qualifiedName}"
        case _ =>
          val tpes = clazz.typeParameters.map { tp =>
            if (tp == effP) {
              "Ƒ"
            } else {
              tp.name
            }
          }
          s"({type λ[Ƒ[_]] = ${clazz.qualifiedName}[${tpes.mkString(",")}]})#λ"
      }

      (tpName, tpText)
    }
  }

  private def mkFunctorK(clazz: ScTypeDefinition): Seq[String] = if (isAutoFunctorK(clazz)) {

    typeParams(clazz).toSeq.flatMap {
      case (tpName, tpText) =>
        Seq(s"implicit def functorKFor${clazz.name}${tpName}: _root_.cats.tagless.FunctorK[${tpText}] = ???")
    }

  } else {
    Seq.empty
  }

  private def mkInstrument(clazz: ScTypeDefinition): Seq[String] = if (isInstrument(clazz)) {

    typeParams(clazz).toSeq.flatMap {
      case (tpName, tpText) =>
        Seq(
          s"implicit def instrumentFor${clazz.name}${tpName}: _root_.cats.tagless.diagnosis.Instrument[${tpText}] = ???"
        )
    }

  } else {
    Seq.empty
  }

  private def mkFinalAlg(clazz: ScTypeDefinition): Seq[String] = if (isFinalAlg(clazz)) {
    val tpName = clazz.typeParameters.map(_.name).mkString(",")
    val tpText = clazz.typeParameters.map(_.typeParameterText).mkString(",")

    Seq(
      s"@scala.inline def apply[$tpText](implicit instance: ${clazz.name}[$tpName]): ${clazz.name}[$tpName] = instance"
    )
  } else {
    Seq.empty
  }

  private def mkWireProtocolKryo(clazz: ScTypeDefinition) = if (isKryoEncoder(clazz)) {

    typeParams(clazz).toSeq.flatMap {
      case (tpName, tpText) =>
        Seq(
          s"implicit def taglessWireProtocol${clazz.name}${tpName}: _root_.com.dispalt.tagless.util.WireProtocol[${tpText}] = ???"
        )
    }

  } else {
    Seq.empty
  }

  private def mkWireProtocolPekko(clazz: ScTypeDefinition) = if (isPekkoEncoder(clazz)) {

    typeParams(clazz).toSeq.flatMap {
      case (tpName, tpText) =>
        Seq(
          s"implicit def taglessWireProtocol${clazz.name}${tpName}(implicit system: _root_.org.apache.pekko.actor.ActorSystem): _root_.com.dispalt.tagless.util.WireProtocol[${tpText}] = ???"
        )
    }

  } else {
    Seq.empty
  }

}
