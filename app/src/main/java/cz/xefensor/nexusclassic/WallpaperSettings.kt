// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager

class WallpaperSettings(private val context: Context, private val loop: RenderLoop) : AutoCloseable {
    companion object { const val FILE = "wallpaper"; const val FPS = "fps" }
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> update() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update()
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        update()
    }
    private fun update() {
        val saver = context.getSystemService(PowerManager::class.java).isPowerSaveMode
        loop.setFps(if (saver) 30 else prefs.getInt(FPS, 60))
    }
    override fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener); context.unregisterReceiver(receiver) }
}
