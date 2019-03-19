package com.dispalt

import com.twitter.bijection.Injection

package object taglessKryo {
  type KryoImpl[A] = Injection[A, Array[Byte]]
}
