package com.goodcover.tagless

import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

package object kryo {
  type KryoImpl[A] = ScalaKryoSerializer
}
