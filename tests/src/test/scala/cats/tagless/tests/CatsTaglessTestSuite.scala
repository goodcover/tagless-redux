package cats.tagless.tests

import cats.instances.AllInstances
import cats.tagless.syntax.AllSyntax
import org.scalatest._

//import scala.util.Try

class CatsTaglessTestSuite extends FunSuite with Matchers with AllSyntax with AllInstances with cats.syntax.AllSyntax
