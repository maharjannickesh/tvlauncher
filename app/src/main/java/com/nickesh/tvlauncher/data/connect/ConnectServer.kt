package com.nickesh.tvlauncher.data.connect

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import java.io.IOException
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
            if (!session.queryParameterString.isNullOrEmpty()) return empty(Response.Status.BAD_REQUEST)
            val rawLength = session.headers["content-length"]
            val length = rawLength?.toLongOrNull() ?: if (rawLength == null) 0L else return empty(Response.Status.BAD_REQUEST)
            if (length > MAX_BODY) return empty(Response.Status.PAYLOAD_TOO_LARGE)
            return try {
                session.parseBody(HashMap())
                val results = runBlocking { handler.submit(session.parms["calendar"], session.parms["token"]) }
                page(results)
            } catch (e: Exception) {
                // Class name only: messages and traces may carry secrets.
                log.warning("connect post failed: ${e.javaClass.simpleName}")
                if (e is IOException || e is ResponseException) empty(Response.Status.BAD_REQUEST)
                else page(listOf(ConnectOutcome.Failed("Something went wrong — try again")))
            }
        }

        private fun page(results: List<ConnectOutcome>): Response {
            val (calendars, masked) = handler.view()
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ConnectPage.html(results, calendars, masked))
                .apply { addHeader("Cache-Control", "no-store") }
        }

        private fun empty(status: Response.Status) = newFixedLengthResponse(status, MIME_PLAINTEXT, "")
        private fun notFound() = empty(Response.Status.NOT_FOUND)
    }

    private companion object {
        val log: Logger = Logger.getLogger("ConnectServer")
        const val MAX_BODY = 8 * 1024L
    }
}
