package com.goodcover.taglessAkka

import akka.actor.ActorSystem
import cats.Id
import cats.tagless.autoFunctorK
import com.goodcover.tagless.util.WireProtocol
import com.goodcover.taglessAkka.akkaEncoderTests.{Bar, Baz, SafeAlg, SafeAlg2}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{matchers, Assertion}

import scala.util.{Failure, Success}
import com.typesafe.config.ConfigFactory

class akkaEncoderTests extends AnyFlatSpec with matchers.should.Matchers {
  behavior of "akkaEncoder"

  val cfg = ConfigFactory.parseString("""
  akka.actor.allow-java-serialization=true
  """).withFallback(ConfigFactory.load())

  implicit val system: ActorSystem = ActorSystem(this.suiteName, cfg)
  import com.goodcover.tagless.TwoWaySimulator._

  it should "generate companion methods" in {
    val wp = WireProtocol[SafeAlg]

    //
    val mf = new SafeAlg[Id] {
      override def test1(i: Int) = {
        println(s"$i")
        i.toString
      }
    }

    val input                = 12
    val output               = mf.test1(input)
    val (payload, resultEnc) = wp.encoder.test1(input)
    val returnPayload        = wp.decoder.apply(payload)

    returnPayload match {
      case Failure(exception) => fail(exception)
      case Success(value) =>
        value.second(value.first.run[Id](mf)) shouldBe AkkaCodecFactory.encode[String].apply(output)
    }
  }

  private def roundTrip[A: AkkaImpl](value: A): Assertion = {
    val enc = AkkaCodecFactory.encode[A].apply(value)
    val dec = AkkaCodecFactory.decode[A].apply(enc)
    dec.get shouldBe value

  }

  it should "anyvals" in {
    roundTrip(25)
    roundTrip(25.0f)
    roundTrip(25.0d)
    roundTrip(25212L)
    roundTrip(254.toByte)
    roundTrip(254.toChar)
    roundTrip(24.toChar)
    roundTrip(())
  }

  it should "anyvals in tuple2" in {

    for { i <- 0 to 1000 } {
      roundTrip(("foo", true))
    }
  }

  it should "handle case classes" in {
    roundTrip(Bar(1))
  }

  it should "handle anyvals" in {
    roundTrip(Baz(1))
  }

  it should "encdec" in {
    val actions = new SafeAlg[Id] {
      override def test1(i: Int) = i.toString
    }

    val fooServer = server(actions)
    val fooClient = client[SafeAlg, Id](fooServer)
    fooClient.test1(1).toEither shouldBe Right("1")
  }

  it should "client/server with extra type param" in {
    val actions = new SafeAlg2[Boolean, Id] {
      override def test2(s: String): Id[(String, Boolean)] = {
        (s, true)
      }
    }

    val fooServer = server(actions)
    val fooClient = client[SafeAlg2[Boolean, *[_]], Id](fooServer)
    fooClient.test2("Fooo").toEither shouldBe Right(("Fooo", true))
  }
}

object akkaEncoderTests {

  @akkaEncoder
  @autoFunctorK
  trait SafeAlg[F[_]] {
    def test1(i: Int): F[String]
  }

  object Hello

  case class Bar(i: Int)

  case class Baz(i: Int) extends AnyVal

  //
  @akkaEncoder
  @autoFunctorK
  trait SafeAlg2[T, E[_]] {
    def test2(s: String): E[(String, T)]
  }

  object SafeAlg2 {}

}
