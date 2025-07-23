package com.goodcover.tagless.kryo.tests

import java.util.UUID

import cats.Id
import com.goodcover.tagless.TwoWaySimulator._
import com.goodcover.tagless.util.WireProtocol
import com.goodcover.tagless.kryo.{ KryoCodec, KryoImpl }
import com.goodcover.tagless.kryo.tests.algs.SafeAlg
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.goodcover.tagless.kryo.Default.kryoInstance

import scala.util.{ Failure, Success }

class KryoEncoderTests extends AnyFlatSpec with Matchers {

  behavior of "kryoEncoder"

  it should "handle any types" in {
    val ab = KryoCodec.encode[(String, (String, Int))].apply(("hello", ("more", 12)))
    val d  = KryoCodec.decode[(String, Any)].apply(ab)

    d match {
      case Failure(exception) => fail(exception)
      case Success(value)     => value._1 shouldBe "hello"
    }
  }

  it should "generate companion methods" in {
    val wp = WireProtocol[SafeAlg]

    val mf = new SafeAlg[Id] {
      override def test(i: Int, foo: String) = {
        println(s"$i, $foo")
        i
      }

      override def test2(i: Int) = {
        println(s"called i=$i")
        i * 2
      }

      override def id = {
        println("called id")
        "called it"
      }
    }

    val input                = 12
    val output               = mf.test2(input)
    val (payload, resultEnc) = wp.encoder.test2(input)
    val returnPayload        = wp.decoder.apply(payload)
    val result3              = returnPayload match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        value.second(value.first.run[Id](mf)) shouldBe KryoCodec.encode[Int].apply(output)
    }

    println(result3)
  }

  it should "encdec" in {
    val uuid    = UUID.randomUUID.toString
    val actions = new SafeAlg[Id] {
      override def test(i: Int, foo: String): Int = i
      override def test2(i: Int): Int             = i
      override def id: String                     = uuid
    }

    val fooServer = server(actions)

    val fooClient = client[SafeAlg, Id](fooServer)

    fooClient.test(1, "foo").toEither shouldBe Right(1)
    fooClient.test2(12).toEither shouldBe Right(12)
    fooClient.id.toEither shouldBe Right(uuid)
  }

  def roundTrip[A](a: A)(using ki: KryoImpl[A]): Assertion = {
    val ab = KryoCodec.encode[A](using ki).apply(a)
    val d  = KryoCodec.decode[A](using ki).apply(ab)
    d.get shouldBe a
  }

  it should "handle high volume" in {
    for { i <- 0 to 20000 }
      roundTrip(true)
  }
}
