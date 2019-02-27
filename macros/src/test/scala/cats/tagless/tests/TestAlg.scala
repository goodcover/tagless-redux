package cats.tagless.tests

import cats.tagless.autoFunctorK

@autoFunctorK
trait TestAlg[F[_]] {
  def more(i: Int): F[Int]
}

@autoFunctorK
trait FooAlg[F[_]] {
  def more(i: Int): F[Int]
}
