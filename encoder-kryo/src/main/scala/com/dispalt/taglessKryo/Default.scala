package com.dispalt.taglessKryo

import java.io.{ByteArrayOutputStream, Serializable}

import com.dispalt.tagless.util.Result
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.pool.{KryoFactory, KryoPool}
import com.twitter.bijection.{Injection, Inversion}
import com.twitter.chill.{IKryoRegistrar, KryoBase, ScalaKryoInstantiator}

import scala.util.Try

object Default {
  implicit def kryoInstance[A]: KryoImpl[A] = LocalInjector.asInstanceOf[KryoImpl[A]]

  object LocalInjector extends Injection[Any, Array[Byte]] {
    private val mutex                      = new AnyRef with Serializable // some serializable object
    @transient private var kpool: KryoPool = null

    private val factory =
      new KryoFactory {

        override def create(): Kryo = new ScalaKryoInstantiator {

          override def newKryo(): KryoBase = {
            val k = super.newKryo()
            ResultRegister(k)
            k
          }
        }.newKryo()
      }

    def defaultPool: KryoPool = mutex.synchronized {
      if (null == kpool) {
        kpool = new KryoPool.Builder(factory).build()
      }
      kpool
    }

    override def apply(a: Any): Array[Byte] = {

      defaultPool.run { kryo =>
        val baos = new ByteArrayOutputStream()
        val out  = new Output(baos)
        try {
          kryo.writeClassAndObject(out, a)
        } finally {
          out.close()
        }
        baos.toByteArray
      }
    }

    override def invert(b: Array[Byte]): Try[Any] = Inversion.attempt(b) { b =>
      defaultPool.run { kryo =>
        val in = new Input(b)
        kryo.readClassAndObject(in).asInstanceOf[Any]
      }
    }
  }

  object ResultRegister extends IKryoRegistrar {

    override def apply(k: Kryo): Unit = {
      k.register(classOf[Result[Any]], new ResultSerializer[Any])
      ()
    }
  }

}
