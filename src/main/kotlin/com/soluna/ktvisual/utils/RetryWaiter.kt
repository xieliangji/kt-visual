package com.soluna.ktvisual.utils

import java.time.Duration

object RetryWaiter {

    fun waitUntil(
        timeout: Duration,
        interval: Duration,
        block: () -> Boolean
    ): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()

        while (System.nanoTime() < deadline) {
            if (block()) return true

            Thread.sleep(interval.toMillis().coerceAtLeast(1))
        }

        return false
    }

    fun <T> waitUntilNotNull(
        timeout: Duration,
        interval: Duration,
        block: () -> T?
    ): T? {
        val deadline = System.nanoTime() + timeout.toNanos()

        while (System.nanoTime() < deadline) {
            val value = block()
            if (value != null) return value

            Thread.sleep(interval.toMillis().coerceAtLeast(1))
        }

        return null
    }
}
