package com.dispalt

import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

package object taglessKryo {
  type KryoImpl[A] = ScalaKryoSerializer
}
