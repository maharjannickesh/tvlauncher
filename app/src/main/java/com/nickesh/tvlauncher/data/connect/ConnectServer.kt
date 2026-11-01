package com.nickesh.tvlauncher.data.connect

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

/** First up, non-loopback, site-local IPv4 address, or null when there is no LAN. */
object LanAddress {
    fun ipv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

/**
 * Serves the phone page at /c/<code> while the Connect screen is open. Anything else is a bare 404.
 * Bodies and queries carry secrets and are never logged.
 */
class ConnectServer(private val handler: ConnectHandler, private val code: String, private val port: Int = 0) {
    private var server: Server? = null

    fun start(): Int {
        val s = Server()
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        server = s
        return s.listeningPort
    }

    fun stop() {
        server?.stop()
        server = null
    }

    val url: (hostIp: String) -> String = { hostIp -> "http://$hostIp:${server?.listeningPort ?: port}/c/$code" }

    private inner class Server : NanoHTTPD(null, port) {
        override fun serve(session: IHTTPSession): Response {
            if (session.uri != "/c/$code") return notFound()
            return when (session.method) {
                Method.GET -> page(emptyList())
                Method.POST -> post(session)
                else -> notFound()
            }
        }

        private fun post(session: IHTTPSession): Response {
            val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
            if (length > MAX_BODY) {
                return newFixedLengthResponse(Response.Status.PAYLOAD_TOO_LARGE, MIME_PLAINTEXT, "")
            }
            session.parseBody(HashMap())
            val results = runBlocking { handler.submit(session.parms["calendar"], session.parms["token"]) }
            return page(results)
        }

        private fun page(results: List<ConnectOutcome>): Response {
            val (calendars, masked) = handler.view()
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ConnectPage.html(results, calendars, masked))
                .apply { addHeader("Cache-Control", "no-store") }
        }

        private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
    }

    private companion object {
        const val MAX_BODY = 8 * 1024L
    }
}
