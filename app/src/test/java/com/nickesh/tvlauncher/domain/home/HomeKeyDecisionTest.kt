package com.nickesh.tvlauncher.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeyDecisionTest {
    private val d = HomeKeyDecision()
    @Test fun shortPress() { assertTrue(d.onDown(1_000)); assertEquals(Press.SHORT, d.onUp(1_080)) }
    @Test fun justUnderThreshold_isShort() { d.onDown(0); assertEquals(Press.SHORT, d.onUp(999)) }
    @Test fun atThreshold_isLong() { d.onDown(0); assertEquals(Press.LONG, d.onUp(1_000)) }
    @Test fun longHold_isLong() { d.onDown(0); assertEquals(Press.LONG, d.onUp(6_000)) }
    @Test fun upWithoutDown_isNone() { assertEquals(Press.NONE, d.onUp(500)) }
    @Test fun secondUp_isNone() { d.onDown(0); d.onUp(80); assertEquals(Press.NONE, d.onUp(90)) }
    @Test fun repeatDownKeepsFirstDownTime() { d.onDown(0); assertTrue(d.onDown(600)); assertEquals(Press.LONG, d.onUp(1_200)) }
    @Test fun customThreshold() { val c = HomeKeyDecision(longPressMs = 1_400); c.onDown(0); assertEquals(Press.SHORT, c.onUp(1_300)) }
}
