package com.dispalt.taglessKryo

import com.twitter.chill.KryoInjection

object Default {
  implicit def kryoInstance[A]: KryoImpl[A] = KryoInjection.asInstanceOf[KryoImpl[A]]

}
