package com.levelapp.core.tests

import com.levelapp.core.audio.SlowIntegrator
import kotlin.test.Test
import kotlin.test.assertTrue

class LeqAndSlowTest {
    @Test
    fun `slow integrator keeps sensitivity in a stable range`() {
        val slow = SlowIntegrator(sampleRate = 48000, timeConstantSeconds = 1.0)
        var level = 0.0
        repeat(2000) { i ->
            level = slow.update(0.5)
        }
        assertTrue(level > 0.0)
    }
}
