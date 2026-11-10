// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Color
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class NexusWallpaperService : WallpaperService() {
    private val engines = java.util.concurrent.CopyOnWriteArraySet<NexusEngine>()
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>) {
        for (engine in engines) writer.println("NexusClassic preview=${engine.isPreview} ${engine.diagnostics()}")
    }
    override fun onCreateEngine(): Engine = NexusEngine()
    inner class NexusEngine : Engine() {
        private lateinit var loop: RenderLoop
        private lateinit var settings: WallpaperSettings
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            loop = RenderLoop(applicationContext)
            settings = WallpaperSettings(applicationContext, loop)
            setOffsetNotificationsEnabled(true)
            engines.add(this)
            // Use launcher commands rather than raw touch events, so icon taps,
            // scrolling and long presses don't create unintended or duplicate bursts.
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            loop.attach(holder.surface, width, height)
            loop.setVisible(isVisible)
        }
        override fun onVisibilityChanged(visible: Boolean) { loop.setVisible(visible) }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { loop.detach(); super.onSurfaceDestroyed(holder) }
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            loop.setOffset(xOffset)
        }
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP || action == WallpaperManager.COMMAND_DROP)
                loop.tap(x.toFloat(), y.toFloat())
            return null
        }
        override fun onComputeColors() = WallpaperColors(Color.valueOf(.10f, .11f, .13f), null, null)
        fun diagnostics() = loop.diagnostics()
        override fun onDestroy() { engines.remove(this); settings.close(); loop.close(); super.onDestroy() }
    }
}
