package com.dispalt.taglessAkka

import akka.actor.ActorSystem
import cats.Id
import cats.tagless.autoFunctorK
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.taglessAkka.akkaEncoderTests.{Bar, SafeAlg}
import org.scalatest.{Assertion, FlatSpec, Matchers}

import scala.util.{Failure, Success}

class akkaEncoderTests extends FlatSpec with Matchers {
  behavior of "akkaEncoder"

  implicit val system: ActorSystem = ActorSystem()

  it should "generate companion methods" in {
    val wp = WireProtocol[SafeAlg]

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

  it should "handle case classes" in {
    roundTrip(Bar(1))
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

}
