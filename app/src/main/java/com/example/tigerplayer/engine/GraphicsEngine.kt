package com.example.tigerplayer.engine.graphics

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 📦 FRAMEBUFFER (FBO)
 * Manages off-screen rendering surfaces.
 */
class FrameBuffer(val width: Int, val height: Int) {
    val texture: Int
    val fbo: Int

    init {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        texture = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)

        // RGBA16F is essential for the Navier-Stokes velocity field
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)

        // Use LINEAR filtering for smooth fluid advection
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        fbo = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

        // SUPREME CHECK: Ensure the hardware actually supports this FBO configuration
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("TigerGraphics", "Incomplete Framebuffer: $status")
        }

        clear()
    }

    fun clear() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    fun write(shader: Shader, useVao: Boolean = false) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        // Viewport must match the simulation resolution, not the screen resolution!
        GLES30.glViewport(0, 0, width, height)
        shader.use()

        // We assume the Master VAO is bound in the Renderer to keep this call lean
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

/**
 * 🏓 PING-PONG BUFFER
 * Essential for simulations where the next state depends on the previous state.
 */

class PingPongBuffer(val width: Int, val height: Int) {
    private var primary = FrameBuffer(width, height)
    private var secondary = FrameBuffer(width, height)

    val readTexture get() = primary.texture
    val writeFbo get() = secondary.fbo

    fun write(shader: Shader, useVao: Boolean = false) {
        secondary.write(shader, useVao)
    }

    fun clear() {
        primary.clear()
        secondary.clear()
    }

    fun swap() {
        val temp = primary
        primary = secondary
        secondary = temp
    }

    fun release() {
        primary.release()
        secondary.release()
    }
}

/**
 * 🔥 SHADER ENGINE
 * Manages compilation and cached uniform locations.
 */
class Shader(vertexSource: String, fragmentSource: String) {
    val programId: Int
    private val uniformCache = HashMap<String, Int>()

    init {
        val vShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vShader)
        GLES30.glAttachShader(programId, fShader)
        GLES30.glLinkProgram(programId)

        GLES30.glDeleteShader(vShader)
        GLES30.glDeleteShader(fShader)
    }

    fun use() = GLES30.glUseProgram(programId)

    private fun getLoc(name: String): Int {
        return uniformCache.getOrPut(name) {
            GLES30.glGetUniformLocation(programId, name)
        }
    }

    fun setUniform(name: String, value: Int) = GLES30.glUniform1i(getLoc(name), value)
    fun setUniform(name: String, value: Float) = GLES30.glUniform1f(getLoc(name), value)
    fun setUniform(name: String, x: Float, y: Float) = GLES30.glUniform2f(getLoc(name), x, y)
    fun setUniform(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(getLoc(name), x, y, z)

    private fun compile(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        return id
    }

    fun release() = GLES30.glDeleteProgram(programId)
}

object FluidMathUtils {
    /**
     * Standard Full-Screen Quad: maps -1..1 (NDC) to 0..1 (UV)
     */
    val fullScreenQuad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }
}