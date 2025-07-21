package com.dispalt.tagless.pekko

/**
 * A new algebra to test the fully generic macro implementation
 */
trait GenericTestAlg[F[_]]:
  def simpleMethod: F[String]
  def singleParam(value: Int): F[Boolean]
  def multipleParams(name: String, age: Int, active: Boolean): F[Long]
  def complexReturn: F[List[Map[String, Int]]]
