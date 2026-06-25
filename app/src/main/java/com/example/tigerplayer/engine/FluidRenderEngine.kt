package com.example.tigerplayer.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.example.tigerplayer.engine.graphics.FrameBuffer
import com.example.tigerplayer.engine.graphics.PingPongBuffer
import com.example.tigerplayer.engine.graphics.Shader
import com.example.tigerplayer.engine.graphics.FluidMathUtils
import com.example.tigerplayer.utils.FluidShaders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.util.concurrent.atomic.AtomicReference
import android.util.Log

/**
 * THE SUPREME GPU VORTEX: FluidRenderer
 * Optimized for 16-bit Float precision and high-dynamic range audio reactivity.
 */
@Singleton
class FluidRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : GLSurfaceView.Renderer {

    data class NodeField(val x: Float, val y: Float, val intensity: Float)

    // Simulation resolution (Power of 2 for optimal GPU texture sampling)
    private val simWidth = 128
    private val simHeight = 128
    private var screenWidth = 0
    private var screenHeight = 0

    private var currentGlContextHash = 0

    // Thread-safe inputs
    private val currentEnergy = AtomicReference<List<Float>>(List(6) { 0f })
    private val currentNodes = AtomicReference<List<NodeField>>(emptyList())
    private val currentTimeline = AtomicReference(Pair(0f, 0f))

    // FBO Resources
    private var velocityRes: PingPongBuffer? = null
    private var densityRes: PingPongBuffer? = null
    private var pressureRes: PingPongBuffer? = null
    private var divergenceRes: FrameBuffer? = null

    private var advectionShader: Shader? = null
    private var divergenceShader: Shader? = null
    private var pressureShader: Shader? = null
    private var gradientSubtractShader: Shader? = null
    private var splatShader: Shader? = null
    private var displayShader: Shader? = null

    private var quadVao: Int = 0
    private var quadVbo: Int = 0
    private var lastFrameTime = System.nanoTime()

    fun updateEnergyField(bands: List<Float>) { currentEnergy.set(bands) }
    fun updateNodeField(nodes: List<NodeField>) { currentNodes.set(nodes) }
    fun updateTimelineEnergy(progress: Float, amp: Float) { currentTimeline.set(Pair(progress, amp)) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val glContextHash = gl.hashCode()
        if (glContextHash == currentGlContextHash) return
        if (currentGlContextHash != 0) release()
        currentGlContextHash = glContextHash

        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        // Initialize high-precision buffers (16-bit Float recommended for Navier-Stokes)
        velocityRes = PingPongBuffer(simWidth, simHeight)
        densityRes = PingPongBuffer(simWidth, simHeight)
        pressureRes = PingPongBuffer(simWidth, simHeight)
        divergenceRes = FrameBuffer(simWidth, simHeight)

        advectionShader = Shader(FluidShaders.vert, FluidShaders.advectionFrag)
        divergenceShader = Shader(FluidShaders.vert, FluidShaders.divergenceFrag)
        pressureShader = Shader(FluidShaders.vert, FluidShaders.pressureFrag)
        gradientSubtractShader = Shader(FluidShaders.vert, FluidShaders.gradientSubtractFrag)
        splatShader = Shader(FluidShaders.vert, FluidShaders.splatFrag)
        displayShader = Shader(FluidShaders.vert, FluidShaders.displayFrag)

        setupQuad()
        lastFrameTime = System.nanoTime()
    }

    private fun setupQuad() {
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        quadVao = vao[0]
        quadVbo = vbo[0]

        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        val quad = FluidMathUtils.fullScreenQuad // Should be -1 to 1 coordinates
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.capacity() * 4, quad, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Lock the internal screen dimensions
        screenWidth = width
        screenHeight = height
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // Clear to TRANSPARENT black (The 0.0f alpha is the key)
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val currentTime = System.nanoTime()
        val dt = ((currentTime - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
        lastFrameTime = currentTime

        // 2. ACTIVATE THE ENGINE
        injectForces()   // We'll fix this next
        stepSimulation(dt)
        renderToDisplay()
    }
    private fun injectForces() {
        val bands = currentEnergy.get()

        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        if (bands.isNotEmpty()) {
            // 1. Extract Bass & Treble for different visual layers
            val bass = (bands[0] + bands[1]) * 0.5f
            val highMid = bands[3]

            if (bass > 0.02f) {
                // PULSE RADIUS: The ball grows based on volume
                val dynamicRadius = 0.12f + (bass * 0.25f).coerceAtMost(0.3f)

                // INTENSITY: More energy = more "White" core (HDR)
                val power = (bass * 25.0f).coerceIn(2f, 40f)

                // CORE VORTEX (White/Blue mix)
                // Center is (0.5, 0.5) - This ensures it stays centered!
                applySplat(0.5f, 0.5f, dynamicRadius, floatArrayOf(power * 0.5f, power * 0.8f, power * 3.0f), true)

                // VELOCITY SWIRL: Rotates based on the High-Mids
                val swirlForce = 5.0f + (highMid * 15f)
                applySplat(0.5f, 0.5f, dynamicRadius * 1.2f, floatArrayOf(swirlForce, -swirlForce, 0f), false)
            }
        }
        GLES30.glBindVertexArray(0)
    }
    private fun applySplat(x: Float, y: Float, r: Float, color: FloatArray, isDensity: Boolean) {
        val shader = splatShader ?: return
        shader.use()
        shader.setUniform("uPoint", x, y)
        shader.setUniform("uColor", color[0], color[1], color[2])
        shader.setUniform("uRadius", r)

        val target = if (isDensity) densityRes else velocityRes
        target?.let {
            // 🔥 FIX: Bind the texture to unit 0 and tell the shader to look at unit 0
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, it.readTexture)
            shader.setUniform("uTarget", 0)

            it.write(shader, false)
            it.swap()
        }
    }
    private fun stepSimulation(dt: Float) {
        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        advectionShader?.let { shader ->
            shader.use()
            shader.setUniform("uDt", dt)

            // 🔥 FIX: Velocity Texture
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            shader.setUniform("uVelocity", 0)

            // Move Velocity
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", 0.995f)
            velocityRes?.write(shader, false)
            velocityRes?.swap()

            // Move Density
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, densityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", 0.985f)
            densityRes?.write(shader, false)
            densityRes?.swap()
        }

        // 2. Projection (Divergence -> Pressure -> Gradient Subtract)
        divergenceShader?.let { divS ->
            divS.use()
            divS.setUniform("uVelocity", velocityRes?.readTexture ?: 0)
            divergenceRes?.write(divS, false)
        }

        pressureShader?.let { presS ->
            presS.use()
            presS.setUniform("uDivergence", divergenceRes?.texture ?: 0)
            // 20 Iterations is the sweet spot for mobile (Stability vs Performance)
            repeat(20) {
                presS.setUniform("uPressure", pressureRes?.readTexture ?: 0)
                pressureRes?.write(presS, false)
                pressureRes?.swap()
            }
        }

        gradientSubtractShader?.let { gradS ->
            gradS.use()
            gradS.setUniform("uPressure", pressureRes?.readTexture ?: 0)
            gradS.setUniform("uVelocity", velocityRes?.readTexture ?: 0)
            velocityRes?.write(gradS, false)
            velocityRes?.swap()
        }

        GLES30.glBindVertexArray(0)
    }

    private fun renderToDisplay() {
        val shader = displayShader ?: return
        val density = densityRes ?: return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader.use()

        // 🔥 FIX: Bind density to unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, density.readTexture)
        shader.setUniform("uDensity", 0)

        GLES30.glBindVertexArray(quadVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }
    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        listOf(velocityRes, densityRes, pressureRes).forEach { it?.release() }
        divergenceRes?.release()
        listOf(advectionShader, divergenceShader, pressureShader, gradientSubtractShader, splatShader, displayShader).forEach { it?.release() }
        currentGlContextHash = 0
    }
}