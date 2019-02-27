package cats.tagless.tests

import scala.util.Try

object Interpreters {

  implicit object tryInterpreter extends SafeAlg[Try] {
    def parseInt(s: String) = Try(s.toInt)

    def divide(dividend: Float, divisor: Float): Try[Float] = Try(dividend / divisor)

    override def divide2: Try[Float] = Try(1.0f)
  }

  implicit object tryInterpreter2 extends FooAlg[Try] {
    override def more(i: Int): Try[Int] = Try(i)
  }

}
