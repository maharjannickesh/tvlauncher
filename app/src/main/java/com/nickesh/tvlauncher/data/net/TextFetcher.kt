package com.nickesh.tvlauncher.data.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface TextFetcher {
    /** Body text of [url]; sends `Authorization: Bearer` when [bearer] is set. Throws [IOException] on transport failure or non-2xx. */
    suspend fun get(url: String, bearer: String? = null): String
}

class OkHttpTextFetcher(private val client: OkHttpClient) : TextFetcher {
    override suspend fun get(url: String, bearer: String?): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { if (bearer != null) header("Authorization", "Bearer $bearer") }.build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("http ${res.code}")
            res.body?.string() ?: throw IOException("empty body")
        }
    }
}
