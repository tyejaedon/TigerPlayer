package com.tigerplayer.engine.graphics

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Manages off-screen OpenGL ES framebuffer objects (FBOs) for ping-pong fluid simulations and post-processing.
 *
 * Encapsulates texture attachment, configuration parameters (internal format, wrapping, filtering),
 * viewport sizing, and cleanup operations.
 *
 * @property width Width of the framebuffer render target in pixels.
 * @property height Height of the framebuffer render target in pixels.
 */
class FrameBuffer(
    val width: Int,
    val height: Int,
    internalFormat: Int = GLES30.GL_RGBA16F,
    format: Int = GLES30.GL_RGBA
) {
    /** The OpenGL texture ID generated for color attachment. */
    val texture: Int
    /** The OpenGL framebuffer object (FBO) identifier. */
    val fbo: Int

    init {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        texture = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)

        // Allocate half-float texture storage for HDR fluid simulation
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            internalFormat,
            width,
            height,
            0,
            format,
            GLES30.GL_HALF_FLOAT,
            null
        )

        // Use LINEAR filtering for smooth fluid advection and clamping to edge
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        fbo = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

        // Verify framebuffer completeness
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("TigerGraphics", "Incomplete Framebuffer: $status")
        }

        clear()
    }

    /** Clears the framebuffer color attachment to transparent black (0, 0, 0, 0). */
    fun clear() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    /**
     * Binds this framebuffer, configures the viewport to match its dimensions, executes the given shader,
     * and draws a full-screen quad.
     *
     * @param shader The [Shader] program to execute during rendering.
     * @param useVao Whether to use vertex array objects (reserved for future extensions).
     */
    fun write(shader: Shader, useVao: Boolean = false) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
        shader.use()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Releases GPU resources (framebuffer and texture objects) associated with this instance. */
    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
}

/**
 * Manages dual ping-pong framebuffers for multi-pass simulations (such as Navier-Stokes fluid advection)
 * where the input of the current step depends on the output of the previous step.
 *
 * Alternates between primary and secondary framebuffers via [swap].
 *
 * @property width Width of both framebuffers in pixels.
 * @property height Height of both framebuffers in pixels.
 */
class PingPongBuffer(
    val width: Int,
    val height: Int,
    internalFormat: Int = GLES30.GL_RGBA16F,
    format: Int = GLES30.GL_RGBA
) {
    private var primary = FrameBuffer(width, height, internalFormat, format)
    private var secondary = FrameBuffer(width, height, internalFormat, format)

    /** Returns the texture ID of the current readable buffer. */
    val readTexture get() = primary.texture
    /** Returns the FBO ID of the current writable buffer. */
    val writeFbo get() = secondary.fbo

    /**
     * Writes into the secondary (writable) buffer using the specified shader.
     *
     * @param shader The [Shader] program to apply.
     * @param useVao Whether to use vertex array objects.
     */
    fun write(shader: Shader, useVao: Boolean = false) {
        secondary.write(shader, useVao)
    }

    /** Clears both primary and secondary framebuffers. */
    fun clear() {
        primary.clear()
        secondary.clear()
    }

    /** Swaps primary and secondary buffers to progress the simulation state. */
    fun swap() {
        val temp = primary
        primary = secondary
        secondary = temp
    }

    /** Releases GPU resources for both internal framebuffers. */
    fun release() {
        primary.release()
        secondary.release()
    }
}

/**
 * Compiles, links, and manages OpenGL ES shader programs and caches uniform variable locations.
 *
 * @param vertexSource GLSL source code for the vertex shader.
 * @param fragmentSource GLSL source code for the fragment shader.
 */
class Shader(vertexSource: String, fragmentSource: String) {
    /** Compiled and linked OpenGL shader program ID. */
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

    /** Activates this shader program in the OpenGL pipeline. */
    fun use() = GLES30.glUseProgram(programId)

    /** Retrieves and caches the location of a uniform variable by name. */
    val getLoc: (String) -> Int = { name ->
        uniformCache.getOrPut(name) {
            GLES30.glGetUniformLocation(programId, name)
        }
    }

    /** Sets an integer uniform variable. */
    fun setUniform(name: String, value: Int) = GLES30.glUniform1i(getLoc(name), value)
    /** Sets a float uniform variable. */
    fun setUniform(name: String, value: Float) = GLES30.glUniform1f(getLoc(name), value)
    /** Sets a 2D vector uniform variable. */
    fun setUniform(name: String, x: Float, y: Float) = GLES30.glUniform2f(getLoc(name), x, y)
    /** Sets a 3D vector uniform variable. */
    fun setUniform(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(getLoc(name), x, y, z)

    /** Compiles an individual shader stage (vertex or fragment). */
    private fun compile(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        return id
    }

    /** Deletes the compiled OpenGL shader program. */
    fun release() = GLES30.glDeleteProgram(programId)
}

/**
 * Utility helper providing shared geometry buffers for OpenGL rendering passes.
 */
object FluidMathUtils {
    /** Standard full-screen quad vertex buffer mapping normalized device coordinates (-1..1) to UV coordinates (0..1). */
    val fullScreenQuad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }
}
