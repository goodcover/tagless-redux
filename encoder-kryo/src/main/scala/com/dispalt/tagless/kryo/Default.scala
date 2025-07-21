package com.dispalt.tagless.kryo

import com.typesafe.config.ConfigFactory
import io.altoo.serialization.kryo.scala.ScalaKryoSerializer

object Default {
  implicit def kryoInstance[A]: KryoImpl[A] = LocalInjector.asInstanceOf[KryoImpl[A]]

  object LocalInjector extends ScalaKryoSerializer(ConfigFactory.load(), getClass.getClassLoader)

}
