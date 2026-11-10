// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderingTest {
    @Test fun previewRendersPausesResumesAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var loop: RenderLoop? = null
            scenario.onActivity { loop = it.previewLoop }
            waitFor { loop!!.frames >= 8 || loop!!.failure != null }
            assertNull(loop!!.failure)
            assertTrue(loop!!.frames >= 8)
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(150)
            val stopped = loop!!.frames
            Thread.sleep(400)
            assertEquals("Hidden preview must not render", stopped, loop!!.frames)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor { loop!!.frames > stopped + 3 || loop!!.failure != null }
            assertNull(loop!!.failure)
            scenario.recreate()
            scenario.onActivity { loop = it.previewLoop }
            waitFor { loop!!.frames >= 5 || loop!!.failure != null }
            assertNull(loop!!.failure)
        }
    }
    @Test fun frameRateSettingSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(WallpaperSettings.FILE, Context.MODE_PRIVATE)
        val old = prefs.getInt(WallpaperSettings.FPS, 60)
        try {
            prefs.edit().putInt(WallpaperSettings.FPS, 30).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.recreate()
                assertEquals(30, prefs.getInt(WallpaperSettings.FPS, 60))
                var loop: RenderLoop? = null
                scenario.onActivity { loop = it.previewLoop }
                waitFor { loop!!.frames >= 3 || loop!!.failure != null }
                assertNull(loop!!.failure)
            }
        } finally { prefs.edit().putInt(WallpaperSettings.FPS, old).commit() }
    }
    @Test fun tapProducesColouredPixelsOnTheRenderedSurface() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var loop: RenderLoop? = null
            var surface: android.view.SurfaceView? = null
            scenario.onActivity { loop = it.previewLoop; surface = it.previewSurface }
            waitFor { loop!!.frames >= 5 || loop!!.failure != null }
            scenario.onActivity { surface!!.performClick() }
            Thread.sleep(500)
            assertNull(loop!!.failure)
            val image = android.graphics.Bitmap.createBitmap(surface!!.width, surface!!.height, android.graphics.Bitmap.Config.ARGB_8888)
            val done = java.util.concurrent.CountDownLatch(1)
            var result = -1
            android.view.PixelCopy.request(surface!!, image, { code -> result = code; done.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(android.view.PixelCopy.SUCCESS, result)
            var coloured = 0
            for (y in 0 until image.height step 4) for (x in 0 until image.width step 4) {
                val c = image.getPixel(x, y)
                val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
                if (maxOf(r,g,b) > 80 && maxOf(r,g,b) - minOf(r,g,b) > 45) coloured++
            }
            image.recycle()
            assertTrue("Tap must produce visible coloured trails, found $coloured pixels", coloured > 50)
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue("Timed out waiting for renderer", condition())
    }
}
