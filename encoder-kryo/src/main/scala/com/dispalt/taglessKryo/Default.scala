package com.dispalt.taglessKryo

import java.io.{ByteArrayOutputStream, Serializable}
import com.dispalt.tagless.util.Result
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.Pool
import io.altoo.akka.serialization.kryo.serializer.scala._

import scala.util.Try

/**
  * Like [[cats.Inject]], but providing more information in the case of a failure
  */
trait Injection[A, B] {
  def apply(a: A): B
  def invert(b: B): Try[A]
}

object Default {
  implicit def kryoInstance[A]: KryoImpl[A] = LocalInjector.asInstanceOf[KryoImpl[A]]

  object LocalInjector extends Injection[Any, Array[Byte]] {
    private val mutex                        = new AnyRef with Serializable // some serializable object
    @transient private var kpool: Pool[Kryo] = null

    def defaultPool: Pool[Kryo] = mutex.synchronized {
      if (null == kpool) {
        kpool = new Pool[Kryo](true, false) {
          override def create(): Kryo = {
            val kryo = new Kryo()
            // Copied from DefaultKryoInitializer

            kryo.addDefaultSerializer(classOf[scala.Enumeration#Value], classOf[EnumerationNameSerializer])
            kryo.register(classOf[scala.Enumeration#Value])

            // identity preserving serializers for Unit and BoxedUnit
            kryo.addDefaultSerializer(classOf[scala.runtime.BoxedUnit], classOf[ScalaUnitSerializer])

            // mutable maps
            kryo.addDefaultSerializer(classOf[scala.collection.mutable.Map[_, _]], classOf[ScalaMutableMapSerializer])

            // immutable maps - specialized by mutable, immutable and sortable
            kryo.addDefaultSerializer(
              classOf[scala.collection.immutable.SortedMap[_, _]],
              classOf[ScalaSortedMapSerializer]
            )
            kryo.addDefaultSerializer(
              classOf[scala.collection.immutable.Map[_, _]],
              classOf[ScalaImmutableMapSerializer]
            )

            // Sets - specialized by mutability and sortability
            kryo.addDefaultSerializer(
              classOf[scala.collection.immutable.BitSet],
              classOf[FieldSerializer[scala.collection.immutable.BitSet]]
            )
            kryo.addDefaultSerializer(
              classOf[scala.collection.immutable.SortedSet[_]],
              classOf[ScalaImmutableSortedSetSerializer]
            )
            kryo.addDefaultSerializer(classOf[scala.collection.immutable.Set[_]], classOf[ScalaImmutableSetSerializer])

            kryo.addDefaultSerializer(
              classOf[scala.collection.mutable.BitSet],
              classOf[FieldSerializer[scala.collection.mutable.BitSet]]
            )
            kryo.addDefaultSerializer(
              classOf[scala.collection.mutable.SortedSet[_]],
              classOf[ScalaMutableSortedSetSerializer]
            )
            kryo.addDefaultSerializer(classOf[scala.collection.mutable.Set[_]], classOf[ScalaMutableSetSerializer])

            // Map/Set Factories
            kryo.addDefaultSerializer(
              classOf[scala.collection.MapFactory[_root_.scala.collection.Map]],
              classOf[ScalaImmutableMapSerializer]
            )
            kryo.addDefaultSerializer(classOf[scala.collection.Iterable[_]], classOf[ScalaCollectionSerializer])

            ResultRegister(kryo)
            kryo
          }
        }
      }
      kpool
    }

    override def apply(a: Any): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val out  = new Output(baos)
      val kryo = defaultPool.obtain()
      try {
        kryo.writeClassAndObject(out, a)
      } finally {
        defaultPool.free(kryo)
        out.close()
      }
      baos.toByteArray
    }

    override def invert(b: Array[Byte]): Try[Any] = Try {
      val in   = new Input(b)
      val kryo = defaultPool.obtain()
      try kryo.readClassAndObject(in).asInstanceOf[Any]
      finally defaultPool.free(kryo)
    }
  }

  object ResultRegister {

    def apply(k: Kryo): Unit = {
      k.register(classOf[Result[Any]], new ResultSerializer[Any])
      ()
    }
  }
}
