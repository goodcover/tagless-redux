package cats.tagless

import scala.reflect.macros.blackbox

object derivation {

  def deriveAutoFunctorK[M[_[_]]]: cats.tagless.FunctorK[M] =
    macro DerivationMacros.autoFunctorKImpl1[M]
}

class DerivationMacros(val c: blackbox.Context) {
  private val utils = new Utils[c.type](c)
  import c.universe._

  def autoFunctorKImpl1[M[_[_]]](implicit wtt: WeakTypeTag[M[Any]]): c.Expr[cats.tagless.FunctorK[M]] = {
    val p = utils.processVal(wtt.tpe.typeSymbol.name.toTypeName, wtt.tpe.typeSymbol)

    if (System.getProperty("tagless.macro.debug", "false") == "true") {
      println(showCode(p.newFunctorKCls))
    }
    c.Expr[cats.tagless.FunctorK[M]](p.newFunctorKCls)
  }
}
