package com.dispalt.taglessBoopickle

import com.dispalt.tagless.util.WireProtocol

object BoopickleWireProtocol {
  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def derive[M[_[_]]]: WireProtocol[M] = macro DeriveMacros.derive[M]
}