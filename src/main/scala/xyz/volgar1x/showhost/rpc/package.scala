package xyz.volgar1x.showhost

import zio.http.URL

package object rpc:
  extension (self: URL) inline def /(path: String): URL = self.copy(path = self.path / path)
