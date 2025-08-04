package com.goodcover.tagless.util

/**
 * Existential pair of type constructors.
 */
sealed abstract class PairE[F[_], G[_]] {
  type A
  def first: F[A]
  def second: G[A]
}

object PairE {

  def apply[F[_], G[_], A](fa: F[A], ga: G[A]): PairE[F, G] = {
    type A0 = A
    new PairE[F, G] {
      override final type A = A0
      override final def first: F[A0]  = fa
      override final def second: G[A0] = ga
      override def toString: String    = s"Pair($first, $second)"
    }
  }
}
