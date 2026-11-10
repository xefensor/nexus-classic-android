// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var loop: RenderLoop
    private lateinit var settings: WallpaperSettings
    internal val previewLoop get() = loop
    internal lateinit var previewSurface: SurfaceView; private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loop = RenderLoop(applicationContext)
        settings = WallpaperSettings(applicationContext, loop)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(17, 19, 23)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val body = LinearLayout(this).apply { orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        header.addView(text(getString(R.string.app_name), 28f, Color.WHITE))
        header.addView(text(getString(R.string.subtitle), 15f, Color.LTGRAY))
        root.addView(header)
        val preview = NexusPreviewView(this, loop)
        previewSurface = preview
        preview.contentDescription = getString(R.string.hint)
        preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { loop.attach(holder.surface, width, height) }
            override fun surfaceDestroyed(holder: SurfaceHolder) { loop.detach() }
        })
        body.addView(preview, if (landscape) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        controls.addView(text(getString(R.string.hint), 13f, Color.LTGRAY))
        controls.addView(text(getString(R.string.frame_rate), 16f, Color.WHITE).apply { setPadding(0, dp(12), 0, 0) })
        val prefs = getSharedPreferences(WallpaperSettings.FILE, MODE_PRIVATE)
        val choices = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val smooth = RadioButton(this).apply { id = View.generateViewId(); setText(R.string.smooth); textSize = 14f }
        val battery = RadioButton(this).apply { id = View.generateViewId(); setText(R.string.battery); textSize = 14f }
        choices.addView(smooth); choices.addView(battery)
        choices.check(if (prefs.getInt(WallpaperSettings.FPS, 60) == 30) battery.id else smooth.id)
        choices.setOnCheckedChangeListener { _, id -> prefs.edit().putInt(WallpaperSettings.FPS, if (id == battery.id) 30 else 60).apply() }
        controls.addView(choices)
        controls.addView(text(getString(R.string.power_hint), 12f, Color.LTGRAY))
        controls.addView(Button(this).apply {
            setText(R.string.preview_apply); isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, NexusWallpaperService::class.java)))
                } catch (_: ActivityNotFoundException) { Toast.makeText(this@MainActivity, R.string.no_picker, Toast.LENGTH_LONG).show() }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(8) })
        val controlsScroll = ScrollView(this).apply { addView(controls); isFillViewport = true }
        body.addView(controlsScroll, if (landscape) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
    }
    override fun onResume() { super.onResume(); loop.setVisible(true) }
    override fun onPause() { loop.setVisible(false); super.onPause() }
    override fun onDestroy() { settings.close(); loop.close(); super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
}
