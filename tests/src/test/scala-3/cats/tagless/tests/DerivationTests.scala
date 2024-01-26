package cats.tagless.tests

import cats.tagless.FunctorK
import cats.tagless.macros.Derive
//import cats.tagless.FunctorK

trait Fooo[F[_]] {
  type T <: Int
  def foo: F[Int]
  def foo2(b: Int): T
}

trait Foo2[T, F[_]] {
  def foo: F[Int]
  def foo2(b: Int): T
}

class DerivationTests extends CatsTaglessTestSuite {

  test("derivation") {

    implicit def fk2[T]: FunctorK[[F[_]] =>> Foo2[T, F]] = Derive.functorK[[F[_]] =>> Foo2[T, F]]
//    implicit def fk: FunctorK[Fooo]                    = Derive.functorK[Fooo]

    locally {
//      fk
      fk2
    }

  }
}
