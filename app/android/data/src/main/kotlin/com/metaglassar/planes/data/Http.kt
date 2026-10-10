package com.metaglassar.planes.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, String>,
) {
  fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/** Minimal HTTP seam so connectors can be tested with canned responses. Throws IOException when offline. */
interface HttpTransport {
  fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

  fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResponse
}

class UrlConnectionTransport(
    private val userAgent: String,
    private val timeoutMs: Int = 15_000,
) : HttpTransport {

  override fun get(url: String, headers: Map<String, String>): HttpResponse =
      request(url, "GET", headers, null)

  override fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResponse {
    val body = form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    return request(url, "POST", headers + ("Content-Type" to "application/x-www-form-urlencoded"), body)
  }

  private fun request(url: String, method: String, headers: Map<String, String>, body: String?): HttpResponse {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = timeoutMs
      conn.readTimeout = timeoutMs
      conn.setRequestProperty("User-Agent", userAgent)
      conn.setRequestProperty("Accept", "application/json")
      headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
      if (body != null) {
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
      }
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
      val responseHeaders =
          conn.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(",") }
      return HttpResponse(code, text, responseHeaders)
    } finally {
      conn.disconnect()
    }
  }

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
