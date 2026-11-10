// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Thread-confined GLES2 sprite renderer. Two bounded sprite batches, no per-pixel pulse loop. */
class NexusRenderer(private val context: Context, surface: Surface) : AutoCloseable {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val eglContext: android.opengl.EGLContext
    private val eglSurface: android.opengl.EGLSurface
    private var backgroundProgram = 0
    private var spriteProgram = 0
    private val textures = IntArray(4)
    private val background = buffer(6 * 8)
    private val trails = buffer(60 * 6 * 8)
    private val glows = buffer(60 * 6 * 8)
    private val colors = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, .8f, 0f),
        floatArrayOf(0f, .4f, .9f), floatArrayOf(1f, .8f, 0f))
    private var trailVertices = 0
    private var glowVertices = 0
    init {
        check(EGL14.eglInitialize(display, null, 0, null, 0)) { "EGL initialize failed" }
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext))
        try {
            backgroundProgram = program(BACKGROUND_FRAGMENT)
            spriteProgram = program(SPRITE_FRAGMENT)
            glGenTextures(4, textures, 0)
            for ((i, name) in arrayOf("tile.png", "lighting.png", "pulse.png", "glow.png").withIndex()) {
                glBindTexture(GL_TEXTURE_2D, textures[i])
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                val bitmap = context.assets.open(name).use { BitmapFactory.decodeStream(it) }
                checkNotNull(bitmap) { "Cannot decode $name" }
                // Android decodes premultiplied alpha; the white pulse RGB is intentionally
                // ignored in the shader, retaining the original straight-alpha blending.
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
                bitmap.recycle()
            }
            glDisable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            check(glGetError() == GL_NO_ERROR) { "GL setup failed" }
        } catch (e: RuntimeException) { close(); throw e }
    }
    fun draw(geometry: SceneGeometry, model: NexusModel, time: Double, offset: Float) {
        glViewport(0, 0, geometry.pixelWidth, geometry.pixelHeight)
        glDisable(GL_BLEND)
        background.clear()
        val left = geometry.offset(offset)
        rect(background, left, 0f, left + geometry.viewWidth, geometry.viewHeight, WHITE)
        background.flip()
        glUseProgram(backgroundProgram)
        glUniform2f(glGetUniformLocation(backgroundProgram, "canvas"), geometry.width, geometry.height)
        texture(backgroundProgram, "lighting", 1, textures[1])
        texture(backgroundProgram, "image", 0, textures[0])
        drawBuffer(backgroundProgram, background, 6, geometry, geometry.offset(offset))

        trails.clear(); glows.clear(); trailVertices = 0; glowVertices = 0
        for (p in model.pulses) {
            if (!p.active || time < p.start) continue
            val x = p.headX(time); val y = p.headY(time)
            val dx = if (p.dx > 0) 1f else if (p.dx < 0) -1f else 0f
            val dy = if (p.dy > 0) 1f else if (p.dy < 0) -1f else 0f
            val c = colors[p.color]
            val half = NexusModel.CELL / 2
            val tail = NexusModel.TRAIL - half
            orientedQuad(trails, x + dx * half, y + dy * half,
                x - dx * tail, y - dy * tail, -dy * half, dx * half, c)
            rect(glows, x - 32, y - 32, x + 32, y + 32, c)
            trailVertices += 6; glowVertices += 6
        }
        trails.flip(); glows.flip()
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glUseProgram(spriteProgram)
        glUniform1i(glGetUniformLocation(spriteProgram, "isGlow"), 0)
        texture(spriteProgram, "image", 0, textures[2])
        drawBuffer(spriteProgram, trails, trailVertices, geometry, geometry.offset(offset))
        glUniform1i(glGetUniformLocation(spriteProgram, "isGlow"), 1)
        texture(spriteProgram, "image", 0, textures[3])
        drawBuffer(spriteProgram, glows, glowVertices, geometry, geometry.offset(offset))
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "EGL swap failed: ${EGL14.eglGetError()}" }
    }
    private fun drawBuffer(program: Int, data: FloatBuffer, count: Int, g: SceneGeometry, offset: Float) {
        if (count == 0) return
        glUniform2f(glGetUniformLocation(program, "viewport"), g.viewWidth, g.viewHeight)
        glUniform1f(glGetUniformLocation(program, "offsetX"), offset)
        for (i in 0..2) {
            data.position(when (i) { 0 -> 0; 1 -> 2; else -> 4 })
            glEnableVertexAttribArray(i)
            glVertexAttribPointer(i, if (i == 2) 4 else 2, GL_FLOAT, false, 32, data)
        }
        glDrawArrays(GL_TRIANGLES, 0, count)
    }
    private fun texture(program: Int, name: String, unit: Int, id: Int) {
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, id)
        glUniform1i(glGetUniformLocation(program, name), unit)
    }
    override fun close() {
        glDeleteTextures(4, textures, 0)
        if (backgroundProgram != 0) glDeleteProgram(backgroundProgram)
        if (spriteProgram != 0) glDeleteProgram(spriteProgram)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, eglContext)
        // eglTerminate is deliberately omitted: preview and wallpaper engines share
        // the process EGLDisplay. Terminating one would invalidate the others.
        EGL14.eglReleaseThread()
    }
    private fun program(fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id)
            val ok = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(id) }
            return id
        }
        val vertex = shader(GL_VERTEX_SHADER, VERTEX)
        val frag = shader(GL_FRAGMENT_SHADER, fragment)
        val id = glCreateProgram()
        glAttachShader(id, vertex); glAttachShader(id, frag)
        glBindAttribLocation(id, 0, "position"); glBindAttribLocation(id, 1, "uv"); glBindAttribLocation(id, 2, "tint")
        glLinkProgram(id); glDeleteShader(vertex); glDeleteShader(frag)
        val ok = IntArray(1); glGetProgramiv(id, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { glGetProgramInfoLog(id) }
        return id
    }
    companion object {
        private val WHITE = floatArrayOf(1f, 1f, 1f)
        private fun buffer(floats: Int) = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private fun vertex(b: FloatBuffer, x: Float, y: Float, u: Float, v: Float, c: FloatArray) {
            b.put(x).put(y).put(u).put(v).put(c[0]).put(c[1]).put(c[2]).put(.8f)
        }
        private fun rect(b: FloatBuffer, l: Float, t: Float, r: Float, bot: Float, c: FloatArray) {
            vertex(b,l,t,0f,0f,c); vertex(b,r,t,1f,0f,c); vertex(b,l,bot,0f,1f,c)
            vertex(b,l,bot,0f,1f,c); vertex(b,r,t,1f,0f,c); vertex(b,r,bot,1f,1f,c)
        }
        private fun orientedQuad(b: FloatBuffer, ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float, c: FloatArray) {
            vertex(b,ax-px,ay-py,0f,0f,c); vertex(b,bx-px,by-py,1f,0f,c); vertex(b,ax+px,ay+py,0f,1f,c)
            vertex(b,ax+px,ay+py,0f,1f,c); vertex(b,bx-px,by-py,1f,0f,c); vertex(b,bx+px,by+py,1f,1f,c)
        }
        private const val VERTEX = """
            attribute vec2 position; attribute vec2 uv; attribute vec4 tint;
            uniform vec2 viewport; uniform float offsetX;
            varying vec2 texcoord; varying vec4 color; varying vec2 world;
            void main() {
                texcoord=uv; color=tint; world=position;
                gl_Position=vec4((position.x-offsetX)/viewport.x*2.0-1.0,1.0-position.y/viewport.y*2.0,0.0,1.0);
            }
        """
        private const val BACKGROUND_FRAGMENT = """
            precision highp float;
            uniform sampler2D image; uniform sampler2D lighting; uniform vec2 canvas;
            varying vec2 world;
            void main() {
                vec3 tile=texture2D(image,fract(world/14.0)).rgb;
                vec3 mean=texture2D(lighting,vec2(483.0/960.0,399.0/800.0)).rgb;
                vec3 light=texture2D(lighting,world/canvas).rgb;
                gl_FragColor=vec4(tile/max(mean,vec3(0.02))*light,1.0);
            }
        """
        private const val SPRITE_FRAGMENT = """
            precision mediump float;
            uniform sampler2D image; uniform bool isGlow;
            varying vec2 texcoord; varying vec4 color;
            void main() {
                vec4 sampleColor=texture2D(image,texcoord);
                gl_FragColor=isGlow ? vec4(sampleColor.rgb*color.rgb,color.a) : vec4(color.rgb,sampleColor.a*color.a);
            }
        """
    }
}
