package com.dispalt.taglessKryo.tests

import java.util.UUID

import com.dispalt.tagless.TwoWaySimulator._
import cats.{Id}
import com.dispalt.taglessKryo.KryoCodec
import com.dispalt.taglessKryo.Default._
import com.dispalt.taglessKryo.tests.algs.SafeAlg
import com.dispalt.tagless.util.WireProtocol
import org.scalatest._

import scala.util.{Failure, Success}

class KryoEncoderTests extends FlatSpec with Matchers {

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
    val result3 = returnPayload match {
      case Failure(exception) => fail(exception)
      case Success(value) =>
        value.second(value.first.run[Id](mf)) shouldBe KryoCodec.encode[Int].apply(output)
    }

    println(result3)

  }

  it should "encdec" in {
    val uuid = UUID.randomUUID.toString
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
}
