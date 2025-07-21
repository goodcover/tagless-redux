package com.dispalt.tagless

import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

package object kryo {
  type KryoImpl[A] = ScalaKryoSerializer
}
