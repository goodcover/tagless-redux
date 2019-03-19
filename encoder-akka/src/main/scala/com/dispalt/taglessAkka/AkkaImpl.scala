package com.dispalt.taglessAkka

import akka.actor.ActorSystem
import akka.serialization.{Serialization, SerializationExtension}

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.Try

@implicitNotFound(
  "Could not find implicit AkkaImpl[${A}] Which usually means you do not have an implicit akka.actor.ActorSystem in scope."
)
trait AkkaImpl[A] {
  def encode(a: A): Array[Byte]
  def decode(a: Array[Byte]): Try[A]
}

object AkkaImpl {
  private[this] var serialization: Option[Serialization] = None
  implicit def akkaImplGen[A](implicit system: ActorSystem, ct: ClassTag[A]): AkkaImpl[A] = {
    val ser = synchronized {
      if (serialization.isEmpty) {
        val matSerializer = SerializationExtension(system)
        serialization = Some(matSerializer)
        matSerializer
      } else serialization.get
    }

    val clazz = ser.serializerFor(ct.runtimeClass)

    new AkkaImpl[A] {
      override def encode(a: A) = clazz.toBinary(a.asInstanceOf[AnyRef])

      override def decode(a: Array[Byte]) = Try(clazz.fromBinary(a).asInstanceOf[A])
    }
  }
}
