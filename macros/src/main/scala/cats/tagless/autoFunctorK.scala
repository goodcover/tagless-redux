package cats.tagless

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.whitebox
import scala.language.experimental.macros

/**
  * auto generates an instance of autoFunctorK
  */
@compileTimeOnly("Cannot expand @autoFunctorK")
class autoFunctorK(autoDerivation: Boolean = true, finalAlg: Boolean = false) extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro FunctorKInstanceGenerator.functorKImpl
}

object FunctorKInstanceGenerator {

  def functorKImpl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    // Get value of autoDerivation
    val (autoDerivation, finalAlg) = c.prefix.tree match {
      case Apply(_, List(Literal(Constant(autoDerive: Boolean)), Literal(Constant(finalAlg: Boolean)))) =>
        (autoDerive, finalAlg)
      case q"new $_(autoDerivation = ${autoDerive: Boolean}, finalAlg = ${finalAlg: Boolean})" => (autoDerive, finalAlg)
      case _                                                                                   => (true, false)
    }

    val result = annottees.map(_.tree).toList match {
      case classDef :: tail =>
        val utils = new Utils[c.type](c).process(classDef, autoDerivation)

        val instanceDef = utils.newDef ::: (if (finalAlg) utils.applyInstanceDef else Nil)

        tail match {
          // if there is a preexisting companion, include it with the updated classDef
          case q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }" :: Nil =>
            val newObj = q"""$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$objDefs
                  ..$instanceDef
                }"""

            List(classDef, newObj)

          // if there is no preexisiting companion
          case Nil =>
            val newObj = q"""object ${utils.className.toTermName} {
                  ..$instanceDef
                }"""

            List(classDef, newObj)

        }

      case Nil => c.abort(c.enclosingPosition, "Expecting a trait.")

    }
    c.Expr[Any](Block(result, Literal(Constant(()))))
  }

}
