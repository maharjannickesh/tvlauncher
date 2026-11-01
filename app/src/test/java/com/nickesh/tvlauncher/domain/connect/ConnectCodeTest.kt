package com.nickesh.tvlauncher.domain.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectCodeTest {
    @Test fun generate_isSixCharsFromAlphabet() {
        repeat(50) {
            val c = ConnectCode.generate()
            assertEquals(6, c.length)
            assertTrue(c.all { it in "abcdefghjkmnpqrstuvwxyz23456789" })
        }
    }
}
