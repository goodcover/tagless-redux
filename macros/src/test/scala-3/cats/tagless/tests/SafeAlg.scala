package cats.tagless.tests

// TODO @finalAlg
trait SafeAlg[F[_]] {
  def parseInt(i: String): F[Int]
  def divide(dividend: Float, divisor: Float): F[Float]
  def divide2: F[Float]
  def divide3: Float
}

object SafeAlg {
////  def apply[F[_]](implicit F: SafeAlg[F]): SafeAlg[F] = F
}
