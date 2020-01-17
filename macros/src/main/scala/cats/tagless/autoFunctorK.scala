package cats.tagless

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.blackbox

/**
  * auto generates an instance of autoFunctorK
  */
@compileTimeOnly("Cannot expand @autoFunctorK")
class autoFunctorK(autoDerivation: Boolean = true) extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro FunctorKInstanceGenerator.functorKImpl
}

object FunctorKInstanceGenerator {

  def functorKImpl(c: blackbox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    // Get value of autoDerivation
    val autoDerivation = c.prefix.tree match {
      case Apply(_, List(Literal(Constant(autoDerive: Boolean)))) =>
        autoDerive
      case q"new $_(autoDerivation = ${autoDerive: Boolean})" => autoDerive
      case _                                                  => true
    }

    val result = annottees.map(_.tree).toList match {
      case classDef :: tail =>
        val utils = new Utils[c.type](c).processAnnotation(classDef, autoDerivation)

        val instanceDef = utils.newDef

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
    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      println(showCode(Block(result, Literal(Constant(())))))
    }
    c.Expr[Any](Block(result, Literal(Constant(()))))
  }

}
