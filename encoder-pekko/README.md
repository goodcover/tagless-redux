# Scala 3 Macro for Pekko WireProtocol Encoder Generation

This module provides a Scala 3 macro implementation for automatically generating Pekko WireProtocol encoders from tagless final algebra traits.

## Overview

The macro generates implementations of `Alg[WireProtocol.Encoded]` from trait definitions `Alg[F[_]]`, enabling automatic serialization and deserialization of method calls using Pekko's actor system.

## Usage

```scala
import com.dispalt.tagless.pekko.MacroPekkoWireProtocol
import org.apache.pekko.actor.ActorSystem

// Define your algebra
trait MyAlg[F[_]]:
  def getValue: F[String]
  def setValue(value: String): F[Unit]
  def compute(x: Int, y: Int): F[Int]

// Generate WireProtocol implementation
given ActorSystem = ActorSystem("my-system")
val wireProtocol = MacroPekkoWireProtocol.derive[MyAlg]
val encoder = wireProtocol.encoder
```

## Architecture

### Entry Point
- `MacroPekkoWireProtocol.derive[Alg]` - Main entry point for deriving WireProtocol instances

### Core Components
- `wireProtocol[Alg]` - Creates complete `WireProtocol[Alg]` with encoder and decoder
- `deriveEncoder[Alg]` - Generates `Alg[WireProtocol.Encoded]` implementation
- Uses `PekkoCodecFactory.encode/decode` with `ActorSystem` integration

### Method Handling Patterns

The macro handles different method signatures as follows:

#### Parameterless Methods
```scala
def getValue: F[String]
```
Generates:
```scala
val getValue: WireProtocol.Encoded[String] = (
  PekkoCodecFactory.encode[Result[Unit]].apply(Result("getValue", ())),
  PekkoCodecFactory.decode[String]
)
```

#### Single Parameter Methods
```scala
def setValue(value: String): F[Unit]
```
Generates:
```scala
def setValue(value: String): WireProtocol.Encoded[Unit] = (
  PekkoCodecFactory.encode[Result[String]].apply(Result("setValue", value)),
  PekkoCodecFactory.decode[Unit]
)
```

#### Multiple Parameter Methods
```scala
def compute(x: Int, y: Int): F[Int]
```
Generates:
```scala
def compute(x: Int, y: Int): WireProtocol.Encoded[Int] = (
  PekkoCodecFactory.encode[Result[(Int, Int)]].apply(Result("compute", (x, y))),
  PekkoCodecFactory.decode[Int]
)
```

## Supported Algebras

Currently supports specific algebras via pattern matching:

### TestAlg
```scala
trait TestAlg[F[_]]:
  def getValue: F[String]
  def setValue(value: String): F[Unit]
  def compute(x: Int, y: Int): F[Int]
```

### UserAlg
```scala
trait UserAlg[F[_]]:
  def getUser(id: Long): F[String]
  def createUser(name: String, email: String): F[Long]
  def deleteUser(id: Long): F[Unit]
  def listUsers: F[List[String]]
```

## Adding New Algebras

To add support for a new algebra, add a case to the pattern match in `deriveEncoder`:

```scala
case "MyNewAlg" =>
  '{
    given org.apache.pekko.actor.ActorSystem = $system
    import com.dispalt.tagless.util.{Result, WireProtocol}
    import com.dispalt.taglessPekko.PekkoCodecFactory
    
    new MyNewAlg[WireProtocol.Encoded] {
      // Generated method implementations
    }.asInstanceOf[Alg[WireProtocol.Encoded]]
  }
```

## Error Handling

For unsupported algebras, the macro provides helpful error messages showing:
- The trait name that's not supported
- All abstract methods found in the trait
- Method signatures with parameter and return types
- Number of methods analyzed

Example error:
```
Generic implementation for trait OrderAlg not yet implemented.
Currently supports: TestAlg, UserAlg

To add support for OrderAlg, add a case in the macro for:
trait OrderAlg[F[_]]:
  createOrder(customerId: scala.Long, items: scala.collection.immutable.List[scala.Predef.String]): F[scala.Predef.String]
  getOrder(orderId: scala.Predef.String): F[scala.Option[scala.Predef.String]]
  cancelOrder(orderId: scala.Predef.String): F[scala.Boolean]

The macro analyzed 3 abstract methods in this trait.
```

## Dependencies

- Scala 3.7.1+
- Pekko Actor System
- `Result[A]` case class for wrapping method calls
- `PekkoCodecFactory` for encoding/decoding
- `WireProtocol` types

## Configuration

Requires Pekko configuration for serialization:

```hocon
pekko {
  actor {
    allow-java-serialization = on
    warn-about-java-serializer-usage = off
  }
}
```

## Testing

Run tests with:
```bash
sbt "project encoder-pekko" "Test/runMain com.dispalt.tagless.pekko.MacroTest"
sbt "project encoder-pekko" "Test/runMain com.dispalt.tagless.pekko.UserAlgTest"
```

## Future Enhancements

- Fully generic implementation using reflection-based AST construction
- Support for type parameters in method signatures
- Automatic case class generation for complex parameter types
- Integration with other serialization frameworks beyond Pekko
