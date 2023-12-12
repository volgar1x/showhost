package xyz.volgar1x.showhost.util

import com.sun.jna.{Library, Native}

import zio.{IO, ZIO}

import java.io.IOException

trait Libc extends Library:
  def getpass(prompt: String): String

lazy val libc = Native.load("c", classOf[Libc])

def getpass(prompt: String): IO[IOException, String] = ZIO.attemptBlockingIO(libc.getpass(prompt))
