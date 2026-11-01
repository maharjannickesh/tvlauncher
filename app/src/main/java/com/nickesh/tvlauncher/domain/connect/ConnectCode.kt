package com.nickesh.tvlauncher.domain.connect

import kotlin.random.Random

/** The short one-time code in the Connect URL. Unambiguous letters and digits only. */
object ConnectCode {
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private const val LENGTH = 6

    fun generate(random: Random = Random.Default): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
