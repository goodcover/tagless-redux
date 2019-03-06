package com.dispalt.tagless

import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class FunctorKInjector extends SyntheticMembersInjector {

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = true

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    val companionClass = source match {
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass
      case _             => null
    }

    companionClass match {
      case clazz: ScClass =>
        FunctorKInjector.mkFunctions(clazz, source.findAnnotation("cats.tagless.autoFunctorK"))
      case _ => Seq.empty
    }
  }
}

object FunctorKInjector {
  // Monocle lenses generation
  private def mkFunctions(clazz: ScClass, annotation: PsiAnnotation): Seq[String] = annotation match {
    case null => Seq.empty
    case _ =>
      val prefix = annotation.findAttributeValue("value") match {
        case ScLiteralImpl.string(value) => value
        case _                           => ""
      }

      mkFunctorK(clazz, prefix)
  }

  private[this] def mkFunctorK(clazz: ScClass, prefix: String): Seq[String] = {
    val typeParametersText = clazz.typeParameters.map(_.getText) match {
      case Seq() => ""
      case seq   => seq.mkString("[", ",", "]")
    }

    Seq(
      s"implicit def functorKInstanceFor${clazz.qualifiedName}: _root_.cats.tagless.FunctorK[${clazz.qualifiedName}] = ???"
    )

    //
//    clazz.allVals
//      .collect {
//        case (f: ScClassParameterImpl, _) if f.isCaseClassVal => f
//      }
//      .map { parameter =>
//        val typeText = if (typeParametersText.isEmpty) {
//          parameter.`type`().toOption.map(_.canonicalText).getOrElse("Any")
//        } else {
//          parameter.typeElement.get.calcType.toString
//        }
//
//        s"def $prefix${parameter.name}$typeParametersText: _root_.monocle.Lens[${clazz.qualifiedName}$typeParametersText, $typeText] = ???"
//      }
  }

}
