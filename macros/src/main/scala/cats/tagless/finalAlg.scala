package cats.tagless

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.whitebox

@compileTimeOnly("Cannot expand @finalAlg")
class finalAlg extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro FinalAlgInstanceGenerator.finalAlgImpl
}

object FinalAlgInstanceGenerator {

  def finalAlgImpl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val result = annottees.map(_.tree).toList match {
      case classDef :: tail =>
        val utils = new Utils[c.type](c).processAnnotation(classDef, false)
        tail match {
          case q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }" :: Nil =>
            val newBody = utils.applyInstanceDef
            val newObj  = q"""$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                 ..$objDefs
                 ..$newBody
                }"""

            List(classDef, newObj)

          case Nil =>
            val newBody = utils.applyInstanceDef
            val newObj  = q"""object ${utils.className.toTermName} {
                  ..$newBody
                }"""
            List(classDef, newObj)
        }

      case Nil => c.abort(c.enclosingPosition, "Expecting a trait")
    }
//    println(showCode(result))
//    result
    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      println(showCode(Block(result, Literal(Constant(())))))
    }
    c.Expr[Any](Block(result, Literal(Constant(()))))
  }
}
