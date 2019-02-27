package cats.tagless.tests

import cats.tagless.autoFunctorK

@autoFunctorK(autoDerivation = true, finalAlg = true)
trait SafeAlg[F[_]] {
  def parseInt(i: String): F[Int]
  def divide(dividend: Float, divisor: Float): F[Float]
}

//object SafeAlg {
////  def apply[F[_]](implicit F: SafeAlg[F]): SafeAlg[F] = F
//}
