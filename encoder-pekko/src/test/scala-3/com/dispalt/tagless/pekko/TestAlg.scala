package com.dispalt.tagless.pekko

// Simple test algebra for macro testing
trait TestAlg[F[_]]:
  def getValue: F[String]
  def setValue(value: String): F[Unit]
  def compute(x: Int, y: Int): F[Int]
