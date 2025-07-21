package com.dispalt.tagless.pekko

import org.apache.pekko.actor.ActorSystem
import cats.Id
import com.dispalt.tagless.util.WireProtocol
import com.dispalt.taglessPekko.PekkoCodecFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{ matchers, Assertion }

import scala.util.{ Failure, Success }
import com.typesafe.config.ConfigFactory

class MultipleParamListTests extends AnyFlatSpec with matchers.should.Matchers {
  behavior of "Multiple Parameter Lists"

  val cfg = ConfigFactory
    .parseString("""
  pekko.actor.allow-java-serialization=true
  """)
    .withFallback(ConfigFactory.load())

  implicit val system: ActorSystem = ActorSystem(this.suiteName, cfg)
  import com.dispalt.tagless.TwoWaySimulator._

  it should "handle methods with multiple parameter lists" in {
    val wp = WireProtocol[MultipleParamListTestAlg]

    val impl = new MultipleParamListTestAlg[Id] {
      override def simpleMethod: Id[String] = "simple"

      override def singleParamList(value: Int): Id[String] = s"single-$value"

      override def multipleParamLists(first: String)(second: Int): Id[String] =
        s"multiple-$first-$second"

      override def threeParamLists(a: String)(b: Int)(c: Boolean): Id[String] =
        s"three-$a-$b-$c"

      override def mixedParamLists(a: String, b: Int)(c: Boolean): Id[String] =
        s"mixed-$a-$b-$c"
    }

    // Test simple method (no parameters)
    val (payload1, resultEnc1) = wp.encoder.simpleMethod
    val returnPayload1         = wp.decoder.apply(payload1)
    returnPayload1 match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        val result = value.first.run[Id](impl)
        value.second(result) shouldBe PekkoCodecFactory.encode[String].apply("simple")
    }

    // Test single parameter list
    val (payload2, resultEnc2) = wp.encoder.singleParamList(42)
    val returnPayload2         = wp.decoder.apply(payload2)
    returnPayload2 match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        val result = value.first.run[Id](impl)
        value.second(result) shouldBe PekkoCodecFactory.encode[String].apply("single-42")
    }

    // Test multiple parameter lists (2 lists, 1 param each)
    val (payload3, resultEnc3) = wp.encoder.multipleParamLists("hello")(123)
    val returnPayload3         = wp.decoder.apply(payload3)
    returnPayload3 match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        val result = value.first.run[Id](impl)
        value.second(result) shouldBe PekkoCodecFactory.encode[String].apply("multiple-hello-123")
    }

    // Test three parameter lists
    val (payload4, resultEnc4) = wp.encoder.threeParamLists("test")(456)(true)
    val returnPayload4         = wp.decoder.apply(payload4)
    returnPayload4 match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        val result = value.first.run[Id](impl)
        value.second(result) shouldBe PekkoCodecFactory.encode[String].apply("three-test-456-true")
    }

    // Test mixed parameter lists (2 params in first list, 1 param in second list)
    val (payload5, resultEnc5) = wp.encoder.mixedParamLists("mixed", 789)(false)
    val returnPayload5         = wp.decoder.apply(payload5)
    returnPayload5 match {
      case Failure(exception) => fail(exception)
      case Success(value)     =>
        val result = value.first.run[Id](impl)
        value.second(result) shouldBe PekkoCodecFactory.encode[String].apply("mixed-mixed-789-false")
    }
  }

  it should "work with client/server simulation" in {
    val impl = new MultipleParamListTestAlg[Id] {
      override def simpleMethod: Id[String]                                   = "simple"
      override def singleParamList(value: Int): Id[String]                    = s"single-$value"
      override def multipleParamLists(first: String)(second: Int): Id[String] = s"multiple-$first-$second"
      override def threeParamLists(a: String)(b: Int)(c: Boolean): Id[String] = s"three-$a-$b-$c"
      override def mixedParamLists(a: String, b: Int)(c: Boolean): Id[String] = s"mixed-$a-$b-$c"
    }

    val server = com.dispalt.tagless.TwoWaySimulator.server(impl)
    val client = com.dispalt.tagless.TwoWaySimulator.client[MultipleParamListTestAlg, Id](server)

    // Test all methods through client/server simulation
    client.simpleMethod.toEither shouldBe Right("simple")
    client.singleParamList(42).toEither shouldBe Right("single-42")
    client.multipleParamLists("hello")(123).toEither shouldBe Right("multiple-hello-123")
    client.threeParamLists("test")(456)(true).toEither shouldBe Right("three-test-456-true")
    client.mixedParamLists("mixed", 789)(false).toEither shouldBe Right("mixed-mixed-789-false")
  }
}
