// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owns a single engine's GL context and Choreographer; all state lives on its thread. */
class RenderLoop(private val context: Context) : AutoCloseable {
    private val thread = HandlerThread("NexusRenderer").apply { start() }
    private val handler = Handler(thread.looper)
    private var choreographer: Choreographer? = null
    private var renderer: NexusRenderer? = null
    private var geometry: SceneGeometry? = null
    private var model: NexusModel? = null
    private val clock = AnimationClock()
    @Volatile private var visible = false
    private var surface: Surface? = null
    private var offset = .5f
    @Volatile private var fps = 60
    @Volatile private var dimensions = "none"
    private var recoveries = 0
    private var queued = false
    private var nextFrameNanos = 0L
    @Volatile private var closed = false
    // Internal diagnostics used by instrumentation; never polled by the production loop.
    @Volatile var frames = 0L; private set
    @Volatile var failure: String? = null; private set

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            queued = false
            if (!visible || renderer == null || closed) return
            try {
                if (frameTimeNanos + 1_000_000L >= nextFrameNanos) {
                    val time = clock.frame(frameTimeNanos)
                    val scene = model ?: return
                    scene.advance(time)
                    renderer!!.draw(geometry!!, scene, time, offset)
                    frames++
                    recoveries = 0
                    val period = 1_000_000_000L / fps
                    nextFrameNanos = if (nextFrameNanos == 0L || frameTimeNanos - nextFrameNanos > period)
                        frameTimeNanos + period else nextFrameNanos + period
                }
                schedule()
            } catch (e: RuntimeException) { fail(e) }
        }
    }
    init { handler.post { choreographer = Choreographer.getInstance() } }
    private fun schedule() {
        if (!queued && visible && renderer != null && !closed) {
            queued = true
            // Skip unused display vsyncs at 90/120 Hz rather than busy-waiting.
            val remainingMs = ((nextFrameNanos - System.nanoTime()) / 1_000_000 - 2).coerceAtLeast(0)
            choreographer?.postFrameCallbackDelayed(callback, remainingMs)
        }
    }
    fun attach(newSurface: Surface, width: Int, height: Int) = post {
        cancel()
        renderer?.close(); renderer = null
        surface = newSurface
        dimensions = "${width}x${height}"
        if (width > 0 && height > 0 && newSurface.isValid) {
            geometry = SceneGeometry(width, height)
            model = NexusModel(geometry!!.width, geometry!!.height)
            // A replacement model starts at the current visible animation time.
            model!!.advance(clock.seconds)
            try {
                renderer = NexusRenderer(context, newSurface)
                newSurface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                failure = null
                schedule()
            } catch (e: RuntimeException) { fail(e) }
        }
    }
    fun setVisible(value: Boolean) = post {
        visible = value
        cancel()
        if (visible) schedule()
    }
    fun setFps(value: Int) = post {
        fps = if (value == 30) 30 else 60
        surface?.takeIf { it.isValid }?.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        nextFrameNanos = 0
    }
    fun setOffset(value: Float) = post { offset = value.coerceIn(0f, 1f) }
    fun tap(pixelX: Float, pixelY: Float) = post {
        val g = geometry ?: return@post
        model?.tap(g.worldX(pixelX, offset), g.worldY(pixelY), clock.seconds)
    }
    private fun post(action: () -> Unit) { if (!closed) handler.post { if (!closed) action() } }
    private fun cancel() {
        choreographer?.removeFrameCallback(callback)
        queued = false
        nextFrameNanos = 0
        clock.pause()
    }
    private fun fail(error: RuntimeException) {
        failure = error.toString()
        Log.e("NexusClassic", "Wallpaper renderer stopped", error)
        cancel()
        renderer?.close(); renderer = null
        if (recoveries++ < 3 && visible) handler.postDelayed({
            val s = surface
            val g = geometry
            if (!closed && visible && s != null && s.isValid && g != null)
                attach(s, g.pixelWidth, g.pixelHeight)
        }, 1000)
    }
    fun diagnostics() = "visible=$visible surface=$dimensions fps=$fps frames=$frames failure=$failure"
    /** Synchronous detach prevents SurfaceHolder destruction racing a queued swap. */
    fun detach() {
        if (closed) return
        val done = CountDownLatch(1)
        handler.post {
            try { cancel(); renderer?.close(); renderer = null; surface = null }
            finally { done.countDown() }
        }
        if (!done.await(3, TimeUnit.SECONDS)) Log.w("NexusClassic", "Surface detach timed out")
    }
    override fun close() {
        if (closed) return
        detach()
        closed = true
        thread.quitSafely()
    }
}
