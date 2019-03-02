package com.dispalt.tagless.kryo.tests

import java.util.UUID

import cats.{~>, Applicative, Functor, Id}
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.tagless.FunctorK
import com.dispalt.tagless.kryo.KryoCodec
import com.dispalt.tagless.kryo.tests.algs.SafeAlg
import com.dispalt.tagless.util.WireProtocol
import cats.tagless.syntax.functorK._
import com.dispalt.tagless.util.WireProtocol.Decoder
import org.scalatest._

import scala.util.{Failure, Success, Try}

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
        value.second(value.first.run[Id](mf)) shouldBe KryoCodec.encode[Int](output)
    }

    println(result3)

  }

  def server[M[_[_]], F[_]: Applicative](
    actions: M[F]
  )(
    implicit M: WireProtocol[M]
  ): Array[Byte] => F[Try[Array[Byte]]] = { in =>
    M.decoder.apply(in) match {
      case Success(p) =>
        val r: F[p.A] = p.first.run(actions)
        r.map(a => Success(p.second.apply(a)))
      case Failure(cause) =>
        (Failure(cause): Try[Array[Byte]]).pure[F]
    }

  }

  type DecodingResultT[F[_], A] = F[Try[A]]

  def client[M[_[_]], F[_]: Functor](
    server: Array[Byte] => F[Try[Array[Byte]]]
  )(
    implicit M: WireProtocol[M],
    MI: FunctorK[M]
  ): M[DecodingResultT[F, ?]] =
    M.encoder
      .mapK[DecodingResultT[F, ?]](new (WireProtocol.Encoded ~> DecodingResultT[F, ?]) {
        override def apply[A](fa: (Array[Byte], Decoder[A])): F[Try[A]] = {
          server(fa._1).map(_.flatMap(fa._2.apply))
        }
      })

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
