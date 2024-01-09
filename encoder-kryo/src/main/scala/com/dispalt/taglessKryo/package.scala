package com.dispalt

package object taglessKryo {
  type KryoImpl[A] = Injection[A, Array[Byte]]
}
