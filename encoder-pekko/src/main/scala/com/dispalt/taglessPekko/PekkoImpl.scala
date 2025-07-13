package com.dispalt.taglessPekko

import com.dispalt.tagless.pekko.AnyValGenerator
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.SerializationExtension

import java.nio.ByteBuffer
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.Try

@implicitNotFound(
  "Could not find implicit PekkoImpl[${A}] " +
    "Which usually means you do not have an implicit org.apache.pekko.actor.ActorSystem in scope."
)
trait PekkoImpl[A] { self =>
  def encode(a: A): Array[Byte]
  def decode(a: Array[Byte]): Try[A]

  def imap[B](enc: A => B)(dec: B => A): PekkoImpl[B] = new PekkoImpl[B] {
    override def encode(a: B) = self.encode(dec(a))

    override def decode(a: Array[Byte]) = self.decode(a).map(enc)
  }
}

object PekkoImpl extends AnyValGenerator {

  private def anyValInstance[A](size: Int, enc: (ByteBuffer, A) => ByteBuffer)(dec: ByteBuffer => A): PekkoImpl[A] =
    new PekkoImpl[A] {

      override def encode(a: A) = {
        val bb = ByteBuffer.allocate(size)
        enc(bb, a).array()
      }

      override def decode(a: Array[Byte]) =
        Try(dec(ByteBuffer.wrap(a)))
    }

  implicit val pekkoImplDouble: PekkoImpl[Double] = anyValInstance[Double](8, _.putDouble(_))(_.getDouble())
  implicit val pekkoImplFloat: PekkoImpl[Float]   = anyValInstance[Float](4, _.putFloat(_))(_.getFloat())
  implicit val pekkoImplLong: PekkoImpl[Long]     = anyValInstance[Long](8, _.putLong(_))(_.getLong())
  implicit val pekkoImplInt: PekkoImpl[Int]       = anyValInstance[Int](4, _.putInt(_))(_.getInt())
  implicit val pekkoImplShort: PekkoImpl[Short]   = anyValInstance[Short](2, _.putShort(_))(_.getShort())
  implicit val pekkoImplByte: PekkoImpl[Byte]     = anyValInstance[Byte](1, _.put(_))(_.get())
  implicit val pekkoImplUnit: PekkoImpl[Unit]     = anyValInstance[Unit](0, (b, _) => b)(_ => ())

  implicit val pekkoImplBoolean: PekkoImpl[Boolean] =
    anyValInstance[Boolean](1, (b, bool) => if (bool) b.put(1.byteValue()) else b.put(0.byteValue()))(b => b.get() == 0x01.toByte)
  implicit val pekkoImplChar: PekkoImpl[Char]       = anyValInstance[Char](2, _.putChar(_))(_.getChar())

}

trait DefaultGenerator {

  implicit def pekkoImplGen[A <: AnyRef](implicit system: ActorSystem, ct: ClassTag[A]): PekkoImpl[A] = {
    val ser   = SerializationExtension(system)
    val clazz = ser.serializerFor(ct.runtimeClass)

    new PekkoImpl[A] {
      override def encode(a: A) = clazz.toBinary(a)

      override def decode(a: Array[Byte]) = Try(clazz.fromBinary(a, Some(ct.runtimeClass)).asInstanceOf[A])
    }
  }
}
