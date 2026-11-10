// SPDX-License-Identifier: Apache-2.0
// Pulse rules adapted from AOSP android-2.2_r1/res/raw/nexus.rs, Copyright 2009 AOSP.
package cz.xefensor.nexusclassic

import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/** Coordinates are original Nexus artwork pixels, with y increasing downwards. */
class SceneGeometry(val pixelWidth: Int, val pixelHeight: Int) {
    val scale = min(pixelWidth, pixelHeight).coerceAtLeast(1) / 480f
    val viewWidth = pixelWidth / scale
    val viewHeight = pixelHeight / scale
    val width = if (pixelHeight >= pixelWidth) viewWidth * 2 else viewWidth
    val height = viewHeight
    fun offset(fraction: Float) = (width - viewWidth) * fraction.coerceIn(0f, 1f)
    fun worldX(pixelX: Float, fraction: Float) = pixelX / scale + offset(fraction)
    fun worldY(pixelY: Float) = pixelY / scale
}

class NexusModel(val width: Float, val height: Float, seed: Int = Random.nextInt()) {
    companion object {
        const val CELL = 14f
        const val TRAIL = 560f
        const val GLOW = 64f
        const val AMBIENT_COUNT = 20
        const val EXTRA_COUNT = 40
    }
    class Pulse {
        var x = 0f; var y = 0f
        var dx = 0f; var dy = 0f
        var start = 0.0
        var color = 0
        var active = false
        fun headX(time: Double) = x + dx * (time - start).toFloat()
        fun headY(time: Double) = y + dy * (time - start).toFloat()
    }
    val pulses = Array(AMBIENT_COUNT + EXTRA_COUNT) { Pulse() }
    private val random = Random(seed)
    init { for (i in 0 until AMBIENT_COUNT) initialize(pulses[i], 0.0) }

    private fun initialize(p: Pulse, time: Double) {
        val half = CELL / 2
        val vertical = random.nextBoolean()
        val speed = 200f * (.7f + .6f * random.nextFloat())
        val positive = random.nextBoolean()
        if (vertical) {
            p.x = floor(random.nextFloat() * width / CELL) * CELL + half
            p.y = if (positive) -half else height - half
            p.dx = 0f; p.dy = if (positive) speed else -speed
        } else {
            p.x = if (positive) half else width + half
            p.y = height - (floor(random.nextFloat() * height / CELL) * CELL + half)
            p.dx = if (positive) speed else -speed; p.dy = 0f
        }
        p.start = time + random.nextInt(2000) / 1000.0
        p.color = random.nextInt(4)
        p.active = true
    }

    fun advance(time: Double) {
        for (i in pulses.indices) {
            val p = pulses[i]
            if (!p.active || time < p.start) continue
            val tail = TRAIL - CELL / 2
            val gone = when {
                p.dx < 0 -> p.headX(time) + tail <= 0
                p.dx > 0 -> p.headX(time) - tail >= width
                p.dy < 0 -> p.headY(time) + tail <= 0
                else -> p.headY(time) - tail >= height
            }
            if (gone) {
                if (i < AMBIENT_COUNT) initialize(p, time) else p.active = false
            }
        }
    }

    fun tap(x: Float, y: Float, time: Double): Int {
        advance(time)
        val snapX = floor(x / CELL) * CELL + CELL / 2
        val snapY = height - (floor((height - y) / CELL) * CELL + CELL / 2)
        val color = random.nextInt(4)
        var count = 0
        for (i in AMBIENT_COUNT until pulses.size) {
            val p = pulses[i]
            if (p.active) continue
            p.x = snapX; p.y = snapY; p.start = time
            p.dx = when (count) { 0 -> 300f; 1 -> -300f; else -> 0f }
            p.dy = when (count) { 2 -> -300f; 3 -> 300f; else -> 0f }
            p.color = (color + count) % 4
            p.active = true
            if (++count == 4) break
        }
        return count
    }
}

/** Accumulates visible time only. Resuming never advances by the hidden interval. */
class AnimationClock {
    var seconds = 0.0; private set
    private var lastNanos = 0L
    fun frame(nanos: Long): Double {
        if (lastNanos != 0L) seconds += (nanos - lastNanos).coerceAtLeast(0) / 1_000_000_000.0
        lastNanos = nanos
        return seconds
    }
    fun pause() { lastNanos = 0 }
}
