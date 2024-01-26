package cats.tagless.tests

import cats.tagless.FunctorK

trait TestAlg[F[_], T] /*derives FunctorK*/ {
  def more(i: Int): F[Int]
  def more2(i: Int): T
}
