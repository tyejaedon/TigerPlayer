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
import kotlin.math.max

/**
 * CONFIGURATION ARCHIVE: The Physical Laws
 * Centralizes magic numbers for audio-reactive fluid tuning.
 */
object FluidConfig {
    const val VELOCITY_DISSIPATION = 0.988f // Reduced slightly for more "viscous" feel
    const val DENSITY_DISSIPATION = 0.980f  // Reduced slightly to feel "heavier"
    const val VORTICITY_CONFINEMENT = 22.0f // Toned down for smoother swirls
    
    // Splat gain constants (tuned for current audio engine calibration)
    const val BASS_SPLAT_RADIUS_BASE = 0.15f
    const val BASS_SPLAT_RADIUS_SCALE = 0.28f
    const val BASS_SPLAT_POWER_SCALE = 32.0f // Boosted for more impact
    const val SWIRL_FORCE_BASE = 4.0f
    const val SWIRL_FORCE_SCALE = 12.0f
    
    // Bandwidth-optimized formats for scalar fields
    const val SCALAR_INTERNAL_FORMAT = GLES30.GL_R16F
    const val SCALAR_FORMAT = GLES30.GL_RED
}

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
    private var curlRes: FrameBuffer? = null

    private var sceneRes: FrameBuffer? = null
    private var bloomPrefilterRes: FrameBuffer? = null
    private var bloomPingRes: FrameBuffer? = null
    private var bloomPongRes: FrameBuffer? = null

    private var advectionShader: Shader? = null
    private var curlShader: Shader? = null
    private var vorticityShader: Shader? = null
    private var divergenceShader: Shader? = null
    private var pressureShader: Shader? = null
    private var gradientSubtractShader: Shader? = null
    private var splatShader: Shader? = null
    private var displayShader: Shader? = null
    private var bloomPrefilterShader: Shader? = null
    private var blurShader: Shader? = null
    private var bloomCompositeShader: Shader? = null

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

        // Initialize high-precision buffers
        // RGBA16F for vectors/colors, R16F for scalar fields to save bandwidth
        velocityRes = PingPongBuffer(simWidth, simHeight)
        densityRes = PingPongBuffer(simWidth, simHeight)
        pressureRes = PingPongBuffer(
            simWidth, simHeight,
            FluidConfig.SCALAR_INTERNAL_FORMAT,
            FluidConfig.SCALAR_FORMAT
        )
        divergenceRes = FrameBuffer(
            simWidth, simHeight,
            FluidConfig.SCALAR_INTERNAL_FORMAT,
            FluidConfig.SCALAR_FORMAT
        )
        curlRes = FrameBuffer(
            simWidth, simHeight,
            FluidConfig.SCALAR_INTERNAL_FORMAT,
            FluidConfig.SCALAR_FORMAT
        )

        advectionShader = Shader(FluidShaders.vert, FluidShaders.advectionFrag)
        curlShader = Shader(FluidShaders.vert, FluidShaders.curlFrag)
        vorticityShader = Shader(FluidShaders.vert, FluidShaders.vorticityFrag)
        divergenceShader = Shader(FluidShaders.vert, FluidShaders.divergenceFrag)
        pressureShader = Shader(FluidShaders.vert, FluidShaders.pressureFrag)
        gradientSubtractShader = Shader(FluidShaders.vert, FluidShaders.gradientSubtractFrag)
        splatShader = Shader(FluidShaders.vert, FluidShaders.splatFrag)
        displayShader = Shader(FluidShaders.vert, FluidShaders.displayFrag)
        bloomPrefilterShader = Shader(FluidShaders.vert, FluidShaders.bloomPrefilterFrag)
        blurShader = Shader(FluidShaders.vert, FluidShaders.blurFrag)
        bloomCompositeShader = Shader(FluidShaders.vert, FluidShaders.bloomCompositeFrag)

        setupQuad()
        if (screenWidth > 0 && screenHeight > 0) {
            rebuildPostFxBuffers(screenWidth, screenHeight)
        }
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
        rebuildPostFxBuffers(width, height)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // Clear to TRANSPARENT black (The 0.0f alpha is the key)
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val currentTime = System.nanoTime()
        // STABILIZATION: Clamp dt to prevent Navier-Stokes explosions while allowing smooth 60fps
        val dt = ((currentTime - lastFrameTime) / 1_000_000_000f).coerceIn(0.008f, 0.022f)
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
                val dynamicRadius = FluidConfig.BASS_SPLAT_RADIUS_BASE + 
                        (bass * FluidConfig.BASS_SPLAT_RADIUS_SCALE).coerceAtMost(0.3f)

                // INTENSITY: More energy = more "White" core (HDR)
                val power = (bass * FluidConfig.BASS_SPLAT_POWER_SCALE).coerceIn(2f, 40f)

                // CORE VORTEX (White/Blue mix)
                // Center is (0.5, 0.5) - This ensures it stays centered!
                applySplat(0.5f, 0.5f, dynamicRadius, floatArrayOf(power * 0.5f, power * 0.8f, power * 3.0f), true)

                // VELOCITY SWIRL: Rotates based on the High-Mids
                val swirlForce = FluidConfig.SWIRL_FORCE_BASE + (highMid * FluidConfig.SWIRL_FORCE_SCALE)
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
        val bands = currentEnergy.get()
        val kick = bands.getOrElse(0) { 0f }.coerceIn(0f, 1f)

        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        advectionShader?.let { shader ->
            shader.use()
            shader.setUniform("uDt", dt)
            shader.setUniform("uKick", kick)

            bindTexture(0, velocityRes?.readTexture ?: 0)
            shader.setUniform("uVelocity", 0)

            // Move Velocity
            bindTexture(1, velocityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", FluidConfig.VELOCITY_DISSIPATION)
            velocityRes?.write(shader, false)
            velocityRes?.swap()

            // Move Density
            bindTexture(0, velocityRes?.readTexture ?: 0)
            bindTexture(1, densityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", FluidConfig.DENSITY_DISSIPATION)
            densityRes?.write(shader, false)
            densityRes?.swap()
        }

        curlShader?.let { curlS ->
            curlS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            curlS.setUniform("uVelocity", 0)
            curlRes?.write(curlS, false)
        }

        vorticityShader?.let { vortS ->
            vortS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            bindTexture(1, curlRes?.texture ?: 0)
            vortS.setUniform("uVelocity", 0)
            vortS.setUniform("uCurl", 1)
            vortS.setUniform("uDt", dt)
            vortS.setUniform("uConfinement", FluidConfig.VORTICITY_CONFINEMENT)
            velocityRes?.write(vortS, false)
            velocityRes?.swap()
        }

        // 2. Projection (Divergence -> Pressure -> Gradient Subtract)
        divergenceShader?.let { divS ->
            divS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            divS.setUniform("uVelocity", 0)
            divergenceRes?.write(divS, false)
        }

        pressureShader?.let { presS ->
            presS.use()
            bindTexture(1, divergenceRes?.texture ?: 0)
            presS.setUniform("uDivergence", 1)
            // 20 Iterations is the sweet spot for mobile (Stability vs Performance)
            repeat(20) {
                bindTexture(0, pressureRes?.readTexture ?: 0)
                presS.setUniform("uPressure", 0)
                pressureRes?.write(presS, false)
                pressureRes?.swap()
            }
        }

        gradientSubtractShader?.let { gradS ->
            gradS.use()
            bindTexture(0, pressureRes?.readTexture ?: 0)
            bindTexture(1, velocityRes?.readTexture ?: 0)
            gradS.setUniform("uPressure", 0)
            gradS.setUniform("uVelocity", 1)
            velocityRes?.write(gradS, false)
            velocityRes?.swap()
        }

        GLES30.glBindVertexArray(0)
    }

    private fun renderToDisplay() {
        val shader = displayShader ?: return
        val density = densityRes ?: return
        val scene = sceneRes ?: return
        val prefilter = bloomPrefilterRes ?: return
        val bloomA = bloomPingRes ?: return
        val bloomB = bloomPongRes ?: return
        val prefilterShader = bloomPrefilterShader ?: return
        val blur = blurShader ?: return
        val composite = bloomCompositeShader ?: return
        val bands = currentEnergy.get()

        val low0 = bands.getOrElse(0) { 0f }
        val low1 = bands.getOrElse(1) { 0f }
        val low2 = bands.getOrElse(2) { 0f }
        val high0 = bands.getOrElse(3) { 0f }
        val high1 = bands.getOrElse(4) { 0f }
        val high2 = bands.getOrElse(5) { 0f }
        val avgEnergy = (low0 + low1 + low2 + high0 + high1 + high2) / 6f

        GLES30.glBindVertexArray(quadVao)

        // Pass 1: density -> chromatic HDR scene
        shader.use()
        bindTexture(0, density.readTexture)
        shader.setUniform("uDensity", 0)
        shader.setUniform("uBandsLow", low0, low1, low2)
        shader.setUniform("uBandsHigh", high0, high1, high2)
        scene.write(shader, false)

        // Pass 2: bright-pass prefilter
        prefilterShader.use()
        bindTexture(0, scene.texture)
        prefilterShader.setUniform("uScene", 0)
        prefilterShader.setUniform("uThreshold", (0.68f - avgEnergy * 0.2f).coerceIn(0.38f, 0.75f))
        prefilter.write(prefilterShader, false)

        // Pass 3: separable blur (2 passes)
        blur.use()
        bindTexture(0, prefilter.texture)
        blur.setUniform("uTexture", 0)
        blur.setUniform("uTexelDir", 1.0f / prefilter.width, 0.0f)
        bloomA.write(blur, false)

        blur.use()
        bindTexture(0, bloomA.texture)
        blur.setUniform("uTexture", 0)
        blur.setUniform("uTexelDir", 0.0f, 1.0f / prefilter.height)
        bloomB.write(blur, false)

        // Pass 4: composite on default framebuffer
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        composite.use()
        bindTexture(0, scene.texture)
        bindTexture(1, bloomB.texture)
        composite.setUniform("uScene", 0)
        composite.setUniform("uBloom", 1)
        composite.setUniform("uBloomStrength", (0.92f + avgEnergy * 1.4f).coerceIn(0.9f, 2.2f))

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }

    private fun bindTexture(unit: Int, textureId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
    }

    private fun rebuildPostFxBuffers(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        sceneRes?.release()
        bloomPrefilterRes?.release()
        bloomPingRes?.release()
        bloomPongRes?.release()

        sceneRes = FrameBuffer(width, height)
        val bloomW = max(1, width / 2)
        val bloomH = max(1, height / 2)
        bloomPrefilterRes = FrameBuffer(bloomW, bloomH)
        bloomPingRes = FrameBuffer(bloomW, bloomH)
        bloomPongRes = FrameBuffer(bloomW, bloomH)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        listOf(velocityRes, densityRes, pressureRes).forEach { it?.release() }
        listOf(divergenceRes, curlRes, sceneRes, bloomPrefilterRes, bloomPingRes, bloomPongRes).forEach { it?.release() }
        listOf(
            advectionShader,
            curlShader,
            vorticityShader,
            divergenceShader,
            pressureShader,
            gradientSubtractShader,
            splatShader,
            displayShader,
            bloomPrefilterShader,
            blurShader,
            bloomCompositeShader
        ).forEach { it?.release() }
        currentGlContextHash = 0
    }
}