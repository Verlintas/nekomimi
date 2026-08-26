/*
 * 猫猫助手 (Nekomimi) — Android accessibility text-rewriting assistant
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.nekomimi.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitBreakerTest {

    private class FakeClock {
        var now = 0L
        fun advance(ms: Long) {
            now += ms
        }
    }

    @Test
    fun `package not blocked initially`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        assertFalse(cb.isPackageBlocked("com.tencent.mm"))
    }

    @Test
    fun `empty package never blocked`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(100) { cb.recordPackageFailure("") }
        assertFalse(cb.isPackageBlocked(""))
    }

    @Test
    fun `repeated failures trip the package`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        val pkg = "com.tencent.mm"
        repeat(5) {
            assertFalse(cb.isPackageBlocked(pkg))
            cb.recordPackageFailure(pkg)
        }
        assertTrue(cb.isPackageBlocked(pkg))
        assertEquals(listOf(pkg), cb.blockedPackages())
    }

    @Test
    fun `trip expires after 30 minutes`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(5) { cb.recordPackageFailure("pkg") }
        assertTrue(cb.isPackageBlocked("pkg"))
        clock.advance(CircuitBreaker.PACKAGE_TRIP_MS + 1)
        assertFalse(cb.isPackageBlocked("pkg"))
    }

    @Test
    fun `old failures expire out of the window`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(4) { cb.recordPackageFailure("pkg") }
        clock.advance(CircuitBreaker.FAILURE_WINDOW_MS + 1)
        repeat(4) { cb.recordPackageFailure("pkg") }
        assertFalse("窗口外失败不应累计到熔断", cb.isPackageBlocked("pkg"))
    }

    @Test
    fun `reset clears failures`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(4) { cb.recordPackageFailure("pkg") }
        cb.resetPackage("pkg")
        repeat(4) { cb.recordPackageFailure("pkg") }
        assertFalse(cb.isPackageBlocked("pkg"))
    }

    @Test
    fun `write storm trips and recovers`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(6) { cb.recordWriteSuccess() }
        assertTrue(cb.isWriteStormActive())
        clock.advance(CircuitBreaker.STORM_TRIP_MS + 1)
        assertFalse(cb.isWriteStormActive())
    }

    @Test
    fun `slow writes do not trip storm`() {
        val clock = FakeClock()
        val cb = CircuitBreaker { clock.now }
        repeat(10) {
            cb.recordWriteSuccess()
            clock.advance(CircuitBreaker.WRITE_WINDOW_MS + 1)
        }
        assertFalse(cb.isWriteStormActive())
    }
}
