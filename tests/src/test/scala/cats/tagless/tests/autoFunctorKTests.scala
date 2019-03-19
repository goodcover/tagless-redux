package cats.tagless.tests

import cats.tagless.{autoFunctorK, finalAlg}
import cats.{~>, Monad, Show}

import scala.util.Try

class autoFunctorKTests extends CatsTaglessTestSuite {
  import autoFunctorKTests._

  test("simple mapk") {
    //
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

  @autoFunctorK
  trait AlgWithNonEffectMethod[F[_]] {
    def a(i: Int): F[Int]
    def b(i: Int): Int
  }

//  @autoFunctorK @finalAlg
//  trait AlgWithTypeMember[F[_]] {
//    type T
//    def a(i: Int): F[T]
//  }
//
//  object AlgWithTypeMember {
//    type Aux[F[_], T0] = AlgWithTypeMember[F] { type T = T0 }
//  }

  @autoFunctorK @finalAlg
  trait AlgWithExtraTP[F[_], T] {
    def a(i: Int): F[T]
  }

  @autoFunctorK @finalAlg
  trait AlgWithExtraTP2[T, F[_]] {
    def a(i: Int): F[T]
  }

  @autoFunctorK
  trait Increment[F[_]] {
    def plusOne(i: Int): F[Int]
  }

  @autoFunctorK(autoDerivation = false)
  trait AlgWithoutAutoDerivation[F[_]] {
    def a(i: Int): F[Int]
  }

  @autoFunctorK
  trait AlgWithDefaultImpl[F[_]] {
    def plusOne(i: Int): F[Int]
    def minusOne(i: Int): Int = i - 1
  }

  @autoFunctorK @finalAlg
  trait AlgWithDef[F[_]] {
    def a: F[Int]
  }

  @autoFunctorK @finalAlg
  trait AlgWithTParamInMethod[F[_]] {
    def a[T](t: T): F[String]
  }

  @autoFunctorK @finalAlg
  trait AlgWithContextBounds[F[_]] {
    def a[T: Show](t: Int): F[String]
  }

//  @autoFunctorK @finalAlg
//  trait AlgWithAbstractTypeClass[F[_]] {
//    type TC[T]
//    def a[T: TC](t: T): F[String]
//  }
//
//  object AlgWithAbstractTypeClass {
//    type Aux[F[_], TC0[_]] = AlgWithAbstractTypeClass[F] { type TC[T] = TC0[T] }
//  }

  @autoFunctorK @finalAlg
  trait AlgWithCurryMethod[F[_]] {
    def a(t: Int)(b: String): F[String]
  }

  @autoFunctorK @finalAlg
  trait AlgWithOwnDerivation[F[_]] {
    def a(b: Int): F[String]
  }

  object AlgWithOwnDerivation {
    implicit def fromMonad[F[_]: Monad]: AlgWithOwnDerivation[F] = new AlgWithOwnDerivation[F] {
      def a(b: Int): F[String] = Monad[F].pure(b.toString)
    }
  }

}
