package com.dispalt.taglessAkka

import akka.actor.ActorSystem
import cats.Id
import cats.tagless.autoFunctorK
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.taglessAkka.akkaEncoderTests.SafeAlg
import org.scalatest.{FlatSpec, Matchers}

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
}

object akkaEncoderTests {

  @akkaEncoder
  @autoFunctorK
  trait SafeAlg[F[_]] {
    def test1(i: Int): F[String]
  }

  object Hello
}
