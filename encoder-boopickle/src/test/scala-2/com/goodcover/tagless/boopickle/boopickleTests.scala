package com.goodcover.tagless.boopickle

import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID
import boopickle.Default._
import cats.Id
import cats.tagless.tests.SafeAlg
import cats.tagless.{ Derive, FunctorK }
import com.goodcover.tagless.TwoWaySimulator._
import com.goodcover.tagless.util.WireProtocol
import com.goodcover.tagless.boopickle.boopickleTests.{ Bar, Baz, FooId, SafeAlg2 }

import scala.util.{ Failure, Success }

object boopickleTests {
  final case class FooId(value: UUID) extends AnyVal

  object Hello

  case class Bar(i: Int)

  case class Baz(i: Int) extends AnyVal

  //

  trait SafeAlg2[T, E[_]] {
    def test2(s: String): E[(String, T)]
  }

  object SafeAlg2 {
    implicit def functorK[T]: FunctorK[SafeAlg2[T, *[_]]]                  = Derive.functorK
    implicit def wireProtocol[T: Pickler]: WireProtocol[SafeAlg2[T, *[_]]] = BoopickleWireProtocol.derive
  }
}

class boopickleTests extends AnyFunSuite with Matchers {

  implicit def functorK: FunctorK[SafeAlg]         = Derive.functorK
  implicit def wireProtocol: WireProtocol[SafeAlg] = BoopickleWireProtocol.derive

  case class TestClass(s: String)

  trait Foo[K, F[_]] {
    def include(i: K): F[Unit]
    def scdsc(s: String): F[K]
    def anotherUUID(s: UUID): F[K]
    def fooBar(s: TestClass): F[Unit]
    def twoParams(s: String)(implicit f: Int): F[Int]
    def id: F[FooId]
  }

  object Foo {

    implicit def functorK[K]: FunctorK[Foo[K, *[_]]]                  = Derive.functorK
    implicit def wireProtocol[K: Pickler]: WireProtocol[Foo[K, *[_]]] = BoopickleWireProtocol.derive
  }

  test("boopickle") {

    val foo: WireProtocol[Foo[FooId, *[_]]] = implicitly[WireProtocol[Foo[FooId, *[_]]]]

    val uuid = UUID.randomUUID

    val mf = new Foo[FooId, Id] {
      override def include(i: FooId): Id[Unit]           = ()
      override def scdsc(s: String): Id[FooId]           = FooId(UUID.fromString(s))
      override def anotherUUID(s: UUID): Id[FooId]       = FooId(s)
      override def fooBar(s: TestClass): Id[Unit]        = ()
      override def twoParams(s: String)(implicit f: Int) = s.length * f
      override def id: Id[FooId]                         = FooId(uuid)
    }

    val (bytes, result) = foo.encoder.scdsc(uuid.toString)
    val result2         = foo.decoder.apply(bytes).get
    val fooIdBytes      = result2.second(result2.first.run[Id](mf))
    result.apply(fooIdBytes).get shouldBe FooId(uuid)
  }

  test("generate companion methods") {
    val wp = WireProtocol[SafeAlg]

    //
    val mf = new SafeAlg[Id] {

      override def parseInt(i: String): Id[Int] = i.toInt

      override def divide(dividend: Float, divisor: Float): Id[Float] = dividend / divisor

      override def divide2: Id[Float] = 1.0f
    }

    val output               = mf.parseInt("12")
    val (payload, resultEnc) = wp.encoder.parseInt("12")
    val returnPayload        = wp.decoder.apply(payload)

    returnPayload match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        value.second(value.first.run[Id](mf)) shouldBe BoopickleCodec.encoder[Int].apply(output)
    }
  }

  private def roundTrip[A: Pickler](value: A): Unit = {
    val enc = BoopickleCodec.encoder[A].apply(value)
    val dec = BoopickleCodec.decoder[A].apply(enc)
    (dec.get shouldBe value): Unit

  }

  test("anyvals") {
    roundTrip(25): Unit
    roundTrip(25.0f): Unit
    roundTrip(25.0d): Unit
    roundTrip(25212L): Unit
    roundTrip(254.toByte): Unit
    roundTrip(254.toChar): Unit
    roundTrip(24.toChar): Unit
    roundTrip(())
  }

  test("anyvals in tuple2") {

    for { _ <- 0 to 1000 }
      roundTrip(("foo", true))
  }

  test("handle case classes") {
    roundTrip(Bar(1))
  }

  test("handle anyvals") {
    roundTrip(Baz(1))
  }

  test("encdec") {
    val actions = new SafeAlg[Id] {
      override def parseInt(i: String): Id[Int] = i.toInt + 10

      override def divide(dividend: Float, divisor: Float): Id[Float] = dividend / divisor

      override def divide2: Id[Float] = 1.0f
    }

    val fooServer = server(actions)
    val fooClient = client[SafeAlg, Id](fooServer)
    fooClient.parseInt("1").toEither shouldBe Right(11)
  }

  test("client/server with extra type param") {
    val actions = new SafeAlg2[Boolean, Id] {
      override def test2(s: String): Id[(String, Boolean)] =
        (s, true)
    }

    val fooServer = server(actions)
    val fooClient = client[SafeAlg2[Boolean, *[_]], Id](fooServer)
    fooClient.test2("Fooo").toEither shouldBe Right(("Fooo", true))
  }
}
