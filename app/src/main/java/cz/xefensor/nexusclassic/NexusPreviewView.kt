// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView

/** Gives raw preview taps and accessibility clicks the same single-burst action. */
// Constructed only in code because it requires an engine-owned renderer.
@android.annotation.SuppressLint("ViewConstructor")
class NexusPreviewView(context: Context, private val loop: RenderLoop) : SurfaceView(context) {
    private var tapX = Float.NaN
    private var tapY = Float.NaN
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tapX = e.x; tapY = e.y
            return performClick()
        }
    })
    init { isClickable = true; isFocusable = true }
    // GestureDetector calls performClick only for a completed tap (see above).
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = gestures.onTouchEvent(event)
    override fun performClick(): Boolean {
        super.performClick()
        loop.tap(if (tapX.isNaN()) width / 2f else tapX, if (tapY.isNaN()) height / 2f else tapY)
        tapX = Float.NaN; tapY = Float.NaN
        return true
    }
}
