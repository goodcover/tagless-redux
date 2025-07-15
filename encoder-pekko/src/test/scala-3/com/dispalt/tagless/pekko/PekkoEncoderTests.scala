package com.dispalt.tagless.pekko

import org.apache.pekko.actor.ActorSystem
import cats.Id
import com.dispalt.tagless.pekko.PekkoEncoderTests.SafeAlg
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.taglessPekko.PekkoCodecFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{ matchers, Assertion }

import scala.util.{ Failure, Success }
import com.typesafe.config.ConfigFactory

class PekkoEncoderTests extends AnyFlatSpec with matchers.should.Matchers {
  behavior of "pekkoEncoder"

  val cfg = ConfigFactory
    .parseString("""
  pekko.actor.allow-java-serialization=true
  """)
    .withFallback(ConfigFactory.load())

  implicit val system: ActorSystem = ActorSystem(this.suiteName, cfg)
  import com.dispalt.tagless.TwoWaySimulator._

  it should "generate companion methods" in {
    val wp = WireProtocol[SafeAlg]

    //
    val mf = new SafeAlg[Id] {
      override def test1(i: Int, r: String) =
        i.toString

      override def test2() = {
        println("test2")
        "test2"
      }
    }

    val input                = 12
    val inputStr             = "2"
    val output               = mf.test1(input, inputStr)
    val (payload, resultEnc) = wp.encoder.test1(input, inputStr)
    val returnPayload        = wp.decoder.apply(payload)

    returnPayload match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        value.second(value.first.run[Id](mf)) shouldBe PekkoCodecFactory.encode[String].apply(output)
    }
  }

}

object PekkoEncoderTests {

  trait SafeAlg[F[_]] {
    def test1(i: Int, r: String): F[String]
    def test2(): F[String]
  }

  object SafeAlg {
    //
    given mkWireProtocol(using system: ActorSystem): WireProtocol[SafeAlg] = MacroPekkoWireProtocol.derive[SafeAlg]

  }

  object Hello

  case class Bar(i: Int)

  case class Baz(i: Int) extends AnyVal

}
