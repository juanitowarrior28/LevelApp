package com.levelapp.core.tests

import com.levelapp.core.audio.SlowIntegrator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class LeqAndSlowTest {
    @Test
    fun `slow integrator keeps sensitivity in a stable range`() {
        val slow = SlowIntegrator(sampleRate = 48000, timeConstantSeconds = 1.0)
        var level = 0.0
        repeat(2000) {
            level = slow.update(0.5)
        }
        assertTrue(level > 0.0)
    }

    @Test
    fun `slow integrator tracks AC sine energy accurately after seeding`() {
        val sampleRate = 48000
        val slow = SlowIntegrator(sampleRate = sampleRate, timeConstantSeconds = 1.0)
        val amp = 10000.0
        val rmsExpected = amp / kotlin.math.sqrt(2.0)
        slow.seedMeanSquare(rmsExpected * rmsExpected)

        var lastRms = 0.0
        for (i in 0 until sampleRate) {
            val x = amp * sin(2.0 * PI * 1000.0 * i / sampleRate)
            lastRms = slow.update(x)
        }
        assertTrue(abs(lastRms - rmsExpected) / rmsExpected < 0.01, "Slow RMS=$lastRms vs $rmsExpected")
        val db = slow.currentDb(referenceRms = 32768.0, referenceDb = 100.0)
        val leq = slow.computeLeq(referenceRms = 32768.0, referenceDb = 100.0)
        assertTrue(abs(db - leq) < 0.1, "Slow dB=$db vs Leq=$leq")
    }
}
