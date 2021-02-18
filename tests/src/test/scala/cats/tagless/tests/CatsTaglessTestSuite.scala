package cats.tagless.tests

import cats.instances.AllInstances
import cats.tagless.syntax.AllSyntax
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

//import scala.util.Try

class CatsTaglessTestSuite extends AnyFunSuite with Matchers with AllSyntax with AllInstances with cats.syntax.AllSyntax
