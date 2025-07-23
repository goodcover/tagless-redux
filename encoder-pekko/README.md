# Scala 3 Macro for Pekko WireProtocol Encoder Generation

This module provides a Scala 3 macro implementation for automatically generating Pekko WireProtocol encoders from tagless final algebra traits.

## Overview

The macro generates implementations of `Alg[WireProtocol.Encoded]` from trait definitions `Alg[F[_]]`, enabling automatic serialization and deserialization of method calls using Pekko's actor system.

## Usage

```scala
import com.goodcover.tagless.pekko.MacroPekkoWireProtocol
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

#### Multiple Parameter Lists (Curried Methods)
```scala
def processData(input: String)(config: Int)(flag: Boolean): F[String]
```
Generates:
```scala
def processData(input: String)(config: Int)(flag: Boolean): WireProtocol.Encoded[String] = (
  PekkoCodecFactory.encode[Result[((String), (Int), (Boolean))]].apply(
    Result("processData", ((input), (config), (flag)))
  ),
  PekkoCodecFactory.decode[String]
)
```

**Note**: Multiple parameter lists are encoded as nested tuples, where each parameter list becomes a tuple element. The decoder uses `Select.unique` to access nested tuple fields properly, ensuring correct parameter extraction during method invocation.

## Supported Algebras

Currently supports specific algebras via pattern matching:


### MultipleParamListTestAlg (Test Algebra)
```scala
trait MultipleParamListTestAlg[F[_]]:
  def simpleMethod: F[String]
  def singleParamList(value: Int): F[String]
  def multipleParamLists(first: String)(second: Int): F[String]
  def threeParamLists(a: String)(b: Int)(c: Boolean): F[String]
  def mixedParamLists(a: String, b: Int)(c: Boolean): F[String]
```

This test algebra demonstrates all supported parameter list patterns and is used to verify the nested tuple handling functionality.



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

## Recent Improvements

### Nested Parameter List Support (v2024.1)

The macro now properly handles methods with multiple parameter lists (curried methods) using nested tuple structures:

**Before**: Multiple parameter lists were flattened, causing incorrect parameter extraction during decoding.

**After**: Each parameter list is preserved as a separate tuple element, enabling proper nested access:

```scala
// Method definition
def method(a: String)(b: Int)(c: Boolean): F[Result]

// Encoding structure
((a), (b), (c))  // Nested tuples preserving parameter list boundaries

// Decoding access pattern
args._1  // First parameter list: a
args._2  // Second parameter list: b
args._3  // Third parameter list: c
```

**Key Changes**:
- Modified `mkDecode` method in `AnyValGenerator.scala` to use nested tuple access
- Replaced flattening logic with proper `Select.unique` calls for nested field access
- Updated method call generation to use multiple `Apply` calls for curried methods
- Added comprehensive test coverage for various parameter list scenarios

**Supported Scenarios**:
- Empty parameter lists: `def method(): F[Result]`
- Single parameter lists: `def method(a: String): F[Result]`
- Multiple parameters in single list: `def method(a: String, b: Int): F[Result]`
- Multiple parameter lists: `def method(a: String)(b: Int): F[Result]`
- Mixed parameter lists: `def method(a: String, b: Int)(c: Boolean): F[Result]`

## Testing

Run tests with:
```bash
sbt "project encoder-pekko" "Test/runMain com.goodcover.tagless.pekko.MacroTest"
sbt "project encoder-pekko" "Test/runMain com.goodcover.tagless.pekko.UserAlgTest"
sbt "project encoder-pekko" "testOnly *MultipleParamListTests"  # Test nested parameter lists
```

## Future Enhancements

- Fully generic implementation using reflection-based AST construction
- Support for type parameters in method signatures
- Automatic case class generation for complex parameter types
- Integration with other serialization frameworks beyond Pekko
- ✅ ~~Support for multiple parameter lists (curried methods)~~ - **Completed in v2024.1**
