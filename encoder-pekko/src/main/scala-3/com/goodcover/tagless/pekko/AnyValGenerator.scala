package com.goodcover.tagless.pekko

import com.goodcover.tagless.*
import com.goodcover.tagless.util.{ CodecFactory, PairE, Result, WireProtocol }
import com.goodcover.tagless.pekko.{ PekkoCodecFactory, PekkoImpl }
import org.apache.pekko.actor.ActorSystem

import scala.annotation.experimental
import scala.quoted.*
import scala.reflect.ClassTag

trait AnyValGenerator extends com.goodcover.tagless.pekko.DefaultGenerator {}

@experimental
object MacroPekkoWireProtocol:
  inline def derive[Alg[_[_]]](using system: ActorSystem): WireProtocol[Alg] = ${
    WireProtocolKryoLike.wireProtocol[Alg, PekkoImpl, PekkoCodecFactory]('{ PekkoCodecFactory })
  }
