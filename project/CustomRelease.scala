package sbtrelease

import sbt._
import sbtrelease.ReleasePlugin.autoImport._

import scala.sys.process.ProcessLogger

object CustomRelease {
  import Utilities._

  private def toProcessLogger(st: State): ProcessLogger = new ProcessLogger {
    override def err(s: => String): Unit = st.log.info(s)
    override def out(s: => String): Unit = st.log.info(s)
    override def buffer[T](f: => T): T   = st.log.buffer(f)
  }

  private def vcs(st: State): Vcs = {
    st.extract
      .get(releaseVcs)
      .getOrElse(sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
  }

  lazy val commitNextVersion = { st: State => commitVersion(st, releaseNextCommitMessage) }

  def commitVersion: (State, TaskKey[String]) => State = { (st: State, commitMessage: TaskKey[String]) =>
    val log     = toProcessLogger(st)
    val file    = st.extract.get(releaseVersionFile).getCanonicalFile
    val base    = vcs(st).baseDir.getCanonicalFile
    val sign    = st.extract.get(releaseVcsSign)
    val signOff = st.extract.get(releaseVcsSignOff)
    val relativePath = IO
      .relativize(base, file)
      .getOrElse("Version file [%s] is outside of this VCS repository with base directory [%s]!" format (file, base))

    vcs(st).add(relativePath) !! log
    val status = vcs(st).status.!!.trim

    val newState = if (status.nonEmpty) {
      val (state, msg) = st.extract.runTask(commitMessage, st)
      vcs(state).commit(msg, sign, signOff) ! log
      state
    } else {
      // nothing to commit. this happens if the version.sbt file hasn't changed.
      st
    }
    newState
  }
}
