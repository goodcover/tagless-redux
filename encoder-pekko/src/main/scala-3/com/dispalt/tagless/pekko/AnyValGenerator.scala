package com.dispalt.tagless.pekko

import com.dispalt.tagless.*
import com.dispalt.tagless.util.{ CodecFactory, PairE, Result, WireProtocol }
import com.dispalt.tagless.pekko.{ PekkoCodecFactory, PekkoImpl }
import org.apache.pekko.actor.ActorSystem

import scala.annotation.experimental
import scala.quoted.*
import scala.reflect.ClassTag

trait AnyValGenerator extends com.dispalt.tagless.pekko.DefaultGenerator {}

@experimental
object MacroPekkoWireProtocol:
  inline def derive[Alg[_[_]]](using system: ActorSystem): WireProtocol[Alg] = ${
    WireProtocolKryoLike.wireProtocol[Alg, PekkoImpl, PekkoCodecFactory]('{ PekkoCodecFactory })
  }
