package cats.tagless.tests

import cats.tagless.autoFunctorK

@autoFunctorK
trait TestAlg[F[_], T] {
  def more(i: Int): F[Int]
  def more2(i: Int): T
}
