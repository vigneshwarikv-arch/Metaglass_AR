package com.metaglassar.planes.data

import java.io.IOException

/** Canned HTTP: each request URL is matched by prefix against [routes]; records every call. */
class FakeHttp(private val routes: MutableMap<String, () -> HttpResponse> = mutableMapOf()) : HttpTransport {
  val calls = mutableListOf<String>()
  var offline = false

  fun on(prefix: String, code: Int, body: String, headers: Map<String, String> = emptyMap()) {
    routes[prefix] = { HttpResponse(code, body, headers) }
  }

  override fun get(url: String, headers: Map<String, String>): HttpResponse = handle("GET $url")

  override fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>) = handle("POST $url")

  private fun handle(call: String): HttpResponse {
    calls += call
    if (offline) throw IOException("offline")
    val url = call.substringAfter(' ')
    val match = routes.keys.filter { url.startsWith(it) }.maxByOrNull { it.length } ?: return HttpResponse(404, "", emptyMap())
    return routes.getValue(match)()
  }
}
