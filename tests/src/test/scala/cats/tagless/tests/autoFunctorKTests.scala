package cats.tagless.tests

import cats.~>
import scala.util.Try

class autoFunctorKTests extends CatsTaglessTestSuite {
  import autoFunctorKTests._

  test("simple mapk") {
    val optionParse: SafeAlg[Option] = Interpreters.tryInterpreter.mapK(fk)

    optionParse.parseInt("3") should be(Some(3))
    optionParse.parseInt("sd") should be(None)
    optionParse.divide(3f, 3f) should be(Some(1f))
    //
  }

  test("simple instance summon with autoDeriveFromFunctorK on") {
    implicit val listParse: SafeAlg[List] = Interpreters.tryInterpreter.mapK(λ[Try ~> List](_.toList))
    SafeAlg[List].parseInt("3") should be(List(3))

    succeed
  }
}

object autoFunctorKTests {
  implicit val fk: Try ~> Option = λ[Try ~> Option](_.toOption)
}
