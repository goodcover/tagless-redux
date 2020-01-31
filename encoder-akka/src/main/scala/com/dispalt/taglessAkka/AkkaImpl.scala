package com.dispalt.taglessAkka

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.Try
import scala.reflect.macros.blackbox.{Context => MacroContext}

@implicitNotFound(
  "Could not find implicit AkkaImpl[${A}] Which usually means you do not have an implicit akka.actor.ActorSystem in scope."
)
trait AkkaImpl[A] { self =>
  def encode(a: A): Array[Byte]
  def decode(a: Array[Byte]): Try[A]

  def imap[B](enc: A => B)(dec: B => A): AkkaImpl[B] = new AkkaImpl[B] {
    override def encode(a: B) = self.encode(dec(a))

    override def decode(a: Array[Byte]) = self.decode(a).map(enc)
  }
}

object AkkaImpl extends AnyValGenerator {

  private def anyValInstance[A](size: Int, enc: (ByteBuffer, A) => ByteBuffer)(dec: ByteBuffer => A): AkkaImpl[A] =
    new AkkaImpl[A] {

      override def encode(a: A) = {
        val bb = ByteBuffer.allocate(size)
        enc(bb, a).array()
      }

      override def decode(a: Array[Byte]) = {
        Try(dec(ByteBuffer.wrap(a)))
      }
    }

  implicit val akkaImplDouble: AkkaImpl[Double] = anyValInstance[Double](8, _.putDouble(_))(_.getDouble())
  implicit val akkaImplFloat: AkkaImpl[Float]   = anyValInstance[Float](4, _.putFloat(_))(_.getFloat())
  implicit val akkaImplLong: AkkaImpl[Long]     = anyValInstance[Long](8, _.putLong(_))(_.getLong())
  implicit val akkaImplInt: AkkaImpl[Int]       = anyValInstance[Int](4, _.putInt(_))(_.getInt())
  implicit val akkaImplShort: AkkaImpl[Short]   = anyValInstance[Short](2, _.putShort(_))(_.getShort())
  implicit val akkaImplByte: AkkaImpl[Byte]     = anyValInstance[Byte](1, _.put(_))(_.get())
  implicit val akkaImplUnit: AkkaImpl[Unit]     = anyValInstance[Unit](0, (b, _) => b)(_ => ())

  implicit val akkaImplBoolean: AkkaImpl[Boolean] =
    anyValInstance[Boolean](1, (b, bool) => if (bool) b.put(1.byteValue()) else b.put(0.byteValue()))(
      b => b.get() == 0x01.toByte
    )
  implicit val akkaImplChar: AkkaImpl[Char] = anyValInstance[Char](2, _.putChar(_))(_.getChar())

}

trait AnyValGenerator extends DefaultGenerator {
  implicit def akkaImplMacroAnyVal[A <: AnyVal]: AkkaImpl[A] = macro AnyValGeneratorMacros.impl[A]
}

trait DefaultGenerator {

  implicit def akkaImplGen[A <: AnyRef](implicit system: ActorSystem, ct: ClassTag[A]): AkkaImpl[A] = {
    val ser   = SerializationExtension(system)
    val clazz = ser.serializerFor(ct.runtimeClass)

    new AkkaImpl[A] {
      override def encode(a: A) = clazz.toBinary(a)

      override def decode(a: Array[Byte]) = Try(clazz.fromBinary(a, Some(ct.runtimeClass)).asInstanceOf[A])
    }
  }
}

class AnyValGeneratorMacros(val c: MacroContext) {
  import c.universe._

  private def withAnyValParam[R](tpe: Type)(f: Symbol => R): Option[R] =
    tpe.baseType(c.symbolOf[AnyVal]) match {
      case NoType => None
      case _ =>
        primaryConstructor(tpe).map(_.paramLists.flatten).collect {
          case param :: Nil => f(param)
        }
    }

  private def primaryConstructor(t: Type) =
    t.members.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m.typeSignature.asSeenFrom(t, t.typeSymbol)
    }

  def impl[T <: AnyVal](implicit t: WeakTypeTag[T]): Expr[AkkaImpl[T]] = {
    c.Expr[AkkaImpl[T]](withAnyValParam(t.tpe) { param =>
      q"""
        implicitly[_root_.com.dispalt.taglessAkka.AkkaImpl[${param.typeSignature}]].imap(new ${t.tpe}(_))((v: ${t.tpe}) => v.${param.name.toTermName})
      """
    }.getOrElse(c.abort(c.enclosingPosition, s"Could find ${t.tpe}")))
  }
}
