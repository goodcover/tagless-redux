package com.dispalt.taglessKryo

import com.dispalt.tagless.util.Result
import com.esotericsoftware.kryo.KryoException
import com.twitter.chill.{Input, KSerializer, Kryo, Output}

class ResultSerializer[A] extends KSerializer[Result[A]] {

  def write(kser: Kryo, out: Output, r: Result[A]): Unit = {
    //Write the string
    out.writeString(r.method)
    kser.writeClassAndObject(out, r.a.asInstanceOf[AnyRef])
  }

  def read(kser: Kryo, in: Input, cls: Class[Result[A]]): Result[A] = {
    try {
      val method = in.readString()
      val result = kser.readClassAndObject(in).asInstanceOf[A]
      Result(method, result)
    } catch {
      case k: KryoException =>
        throw k
    }
  }
}
