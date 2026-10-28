package com.tigerplayer.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.tigerplayer.engine.graphics.FrameBuffer
import com.tigerplayer.engine.graphics.PingPongBuffer
import com.tigerplayer.engine.graphics.Shader
import com.tigerplayer.engine.graphics.FluidMathUtils
import com.tigerplayer.utils.FluidShaders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Configuration parameters and physical tuning constants for audio-reactive fluid dynamics.
 * Centralizes dissipation rates, vorticity confinement, splat radii, and texture internal formats.
 */
object FluidConfig {
    /** Velocity dissipation rate per frame (closer to 1.0 retains momentum longer). */
    const val VELOCITY_DISSIPATION = 0.988f
    /** Density dissipation rate per frame governing smoke/color fade speed. */
    const val DENSITY_DISSIPATION = 0.980f
    /** Vorticity confinement strength maintaining swirling eddy details. */
    const val VORTICITY_CONFINEMENT = 22.0f

    /** Base radius for bass-reactive fluid splats. */
    const val BASS_SPLAT_RADIUS_BASE = 0.15f
    /** Scale factor multiplying bass energy to expand splat radius. */
    const val BASS_SPLAT_RADIUS_SCALE = 0.28f
    /** Power scale multiplying bass energy for HDR brightness impact. */
    const val BASS_SPLAT_POWER_SCALE = 32.0f
    /** Base force for rotational swirl injection. */
    const val SWIRL_FORCE_BASE = 4.0f
    /** Scale factor multiplying high-mid energy for rotational force. */
    const val SWIRL_FORCE_SCALE = 12.0f

    /** Bandwidth-optimized internal texture format for scalar simulation fields (R16F). */
    const val SCALAR_INTERNAL_FORMAT = GLES30.GL_R16F
    /** Pixel data format corresponding to scalar fields. */
    const val SCALAR_FORMAT = GLES30.GL_RED
}

/**
 * GPU-accelerated fluid dynamics renderer implementing real-time Navier-Stokes simulations
 * driven by audio frequency band energies.
 *
 * Implements [GLSurfaceView.Renderer] to execute ping-pong advection, diffusion, vorticity,
 * pressure projection, and post-processing bloom effects on OpenGL ES 3.0.
 *
 * @property context Application context injected by Hilt.
 */
@Singleton
class FluidRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : GLSurfaceView.Renderer {

    /** Represents a localized touch or audio node force input field. */
    data class NodeField(val x: Float, val y: Float, val intensity: Float)

    /** Internal simulation grid width (power of 2 for GPU texture efficiency). */
    private val simWidth = 128
    /** Internal simulation grid height (power of 2 for GPU texture efficiency). */
    private val simHeight = 128
    /** Current surface viewport width in pixels. */
    private var screenWidth = 0
    /** Current surface viewport height in pixels. */
    private var screenHeight = 0

    /** Hash of the active EGL context to detect context loss and re-initialize. */
    private val currentGlContextHash = java.util.concurrent.atomic.AtomicInteger(0)

    // Thread-safe atomic references for asynchronous audio engine inputs
    private val currentEnergy = AtomicReference<List<Float>>(List(6) { 0f })
    private val currentNodes = AtomicReference<List<NodeField>>(emptyList())
    private val currentTimeline = AtomicReference(Pair(0f, 0f))

    // Simulation ping-pong and FBO resources
    private var velocityRes: PingPongBuffer? = null
    private var densityRes: PingPongBuffer? = null
    private var pressureRes: PingPongBuffer? = null
    private var divergenceRes: FrameBuffer? = null
    private var curlRes: FrameBuffer? = null

    // Post-processing framebuffers for HDR scene rendering and bloom
    private var sceneRes: FrameBuffer? = null
    private var bloomPrefilterRes: FrameBuffer? = null
    private var bloomPingRes: FrameBuffer? = null
    private var bloomPongRes: FrameBuffer? = null

    // Shader program handles
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

    /** Vertex array object ID for full-screen quad rendering. */
    private var quadVao: Int = 0
    /** Vertex buffer object ID for full-screen quad rendering. */
    private var quadVbo: Int = 0
    /** Timestamp of the previous frame in nanoseconds for delta-time calculation. */
    private var lastFrameTime = System.nanoTime()

    /** Updates the audio frequency band energies driving fluid forces. */
    fun updateEnergyField(bands: List<Float>) { currentEnergy.set(bands) }
    /** Updates interactive node fields. */
    fun updateNodeField(nodes: List<NodeField>) { currentNodes.set(nodes) }
    /** Updates timeline progress and amplitude metrics. */
    fun updateTimelineEnergy(progress: Float, amp: Float) { currentTimeline.set(Pair(progress, amp)) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val glContextHash = gl.hashCode()
        if (glContextHash == currentGlContextHash.get()) return
        if (currentGlContextHash.get() != 0) release()
        currentGlContextHash.set(glContextHash)

        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        // Initialize simulation ping-pong buffers and FBOs
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

        // Compile and link simulation and post-processing shaders
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

    /** Sets up the vertex array object and vertex buffer object for rendering a full-screen quad. */
    private fun setupQuad() {
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        quadVao = vao[0]
        quadVbo = vbo[0]

        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        val quad = FluidMathUtils.fullScreenQuad
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.capacity() * 4, quad, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        rebuildPostFxBuffers(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val currentTime = System.nanoTime()
        val dt = ((currentTime - lastFrameTime) / 1_000_000_000f).coerceIn(0.008f, 0.022f)
        lastFrameTime = currentTime

        injectForces()
        stepSimulation(dt)
        renderToDisplay()
    }

    /** Injects audio-reactive forces and density splats into the velocity and density fields. */
    private fun injectForces() {
        val bands = currentEnergy.get()

        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        val timeSec = System.nanoTime() / 1_000_000_000f
        val bass = if (bands.isNotEmpty()) (bands[0] + bands[1]) * 0.5f else 0f
        val highMid = if (bands.size > 3) bands[3] else 0f

        if (bass > 0.02f) {
            val dynamicRadius = FluidConfig.BASS_SPLAT_RADIUS_BASE +
                    (bass * FluidConfig.BASS_SPLAT_RADIUS_SCALE).coerceAtMost(0.3f)
            val power = (bass * FluidConfig.BASS_SPLAT_POWER_SCALE).coerceIn(2f, 40f)

            // Inject density splat at the center
            applySplat(0.5f, 0.5f, dynamicRadius, floatArrayOf(power * 0.5f, power * 0.8f, power * 3.0f), true)

            // Inject rotational velocity swirl
            val swirlForce = FluidConfig.SWIRL_FORCE_BASE + (highMid * FluidConfig.SWIRL_FORCE_SCALE)
            applySplat(0.5f, 0.5f, dynamicRadius * 1.2f, floatArrayOf(swirlForce, -swirlForce, 0f), false)
        } else {
            // Ambient idle breathing swirl so the visualizer is never pitch black
            val ambientCycle = (kotlin.math.sin(timeSec.toDouble() * 0.8) * 0.5 + 0.5).toFloat()
            val ambientX = 0.5f + (kotlin.math.cos(timeSec.toDouble() * 0.5) * 0.15).toFloat()
            val ambientY = 0.5f + (kotlin.math.sin(timeSec.toDouble() * 0.5) * 0.15).toFloat()
            val ambientRadius = FluidConfig.BASS_SPLAT_RADIUS_BASE * (0.8f + ambientCycle * 0.4f)
            val ambientPower = 1.8f + ambientCycle * 2.2f

            applySplat(ambientX, ambientY, ambientRadius, floatArrayOf(ambientPower * 0.4f, ambientPower * 0.7f, ambientPower * 2.2f), true)
            applySplat(ambientX, ambientY, ambientRadius * 1.1f, floatArrayOf(2.5f, -2.5f, 0f), false)
        }
        GLES30.glBindVertexArray(0)
    }

    /** Applies a single force or density splat at given normalized coordinates. */
    private fun applySplat(x: Float, y: Float, r: Float, color: FloatArray, isDensity: Boolean) {
        val shader = splatShader ?: return
        shader.use()
        shader.setUniform("uPoint", x, y)
        shader.setUniform("uColor", color[0], color[1], color[2])
        shader.setUniform("uRadius", r)

        val target = if (isDensity) densityRes else velocityRes
        target?.let {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, it.readTexture)
            shader.setUniform("uTarget", 0)

            it.write(shader, false)
            it.swap()
        }
    }

    /** Advances the Navier-Stokes fluid simulation by one time step ([dt]). */
    private fun stepSimulation(dt: Float) {
        val bands = currentEnergy.get()
        val kick = bands.getOrElse(0) { 0f }.coerceIn(0f, 1f)
        val lowEnergy = (bands.getOrElse(0) { 0f } + bands.getOrElse(1) { 0f } + bands.getOrElse(2) { 0f }) / 3f
        val highEnergy = (bands.getOrElse(3) { 0f } + bands.getOrElse(4) { 0f } + bands.getOrElse(5) { 0f }) / 3f
        val expansion = ((lowEnergy - highEnergy) * 0.08f).coerceIn(-0.09f, 0.09f)
        val flowSpeed = (0.90f + lowEnergy * 0.42f + kick * 0.33f).coerceIn(0.75f, 1.75f)
        val turbulence = (0.08f + highEnergy * 0.26f + kick * 0.18f).coerceIn(0.04f, 0.55f)
        val texelX = 1f / simWidth.toFloat()
        val texelY = 1f / simHeight.toFloat()
        val timeSec = System.nanoTime() / 1_000_000_000f

        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        // 1. Advection
        advectionShader?.let { shader ->
            shader.use()
            shader.setUniform("uDt", dt)
            shader.setUniform("uKick", kick)
            shader.setUniform("uExpansion", expansion)
            shader.setUniform("uFlowSpeed", flowSpeed)
            shader.setUniform("uTurbulence", turbulence)
            shader.setUniform("uTime", timeSec)

            bindTexture(0, velocityRes?.readTexture ?: 0)
            shader.setUniform("uVelocity", 0)

            bindTexture(1, velocityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", FluidConfig.VELOCITY_DISSIPATION)
            velocityRes?.write(shader, false)
            velocityRes?.swap()

            bindTexture(0, velocityRes?.readTexture ?: 0)
            bindTexture(1, densityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", FluidConfig.DENSITY_DISSIPATION)
            densityRes?.write(shader, false)
            densityRes?.swap()
        }

        // Curl and Vorticity Confinement
        curlShader?.let { curlS ->
            curlS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            curlS.setUniform("uVelocity", 0)
            curlS.setUniform("uTexelSize", texelX, texelY)
            curlRes?.write(curlS, false)
        }

        vorticityShader?.let { vortS ->
            vortS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            bindTexture(1, curlRes?.texture ?: 0)
            vortS.setUniform("uVelocity", 0)
            vortS.setUniform("uCurl", 1)
            vortS.setUniform("uTexelSize", texelX, texelY)
            vortS.setUniform("uDt", dt)
            vortS.setUniform("uCurlStrength", FluidConfig.VORTICITY_CONFINEMENT * (1f + kick * 0.35f))
            velocityRes?.write(vortS, false)
            velocityRes?.swap()
        }

        // Pressure Projection
        divergenceShader?.let { divS ->
            divS.use()
            bindTexture(0, velocityRes?.readTexture ?: 0)
            divS.setUniform("uVelocity", 0)
            divS.setUniform("uExpansion", expansion)
            divS.setUniform("uTexelSize", texelX, texelY)
            divergenceRes?.write(divS, false)
        }

        pressureShader?.let { presS ->
            presS.use()
            bindTexture(1, divergenceRes?.texture ?: 0)
            presS.setUniform("uDivergence", 1)
            presS.setUniform("uExpansion", expansion)
            presS.setUniform("uTexelSize", texelX, texelY)
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
            gradS.setUniform("uTexelSize", texelX, texelY)
            velocityRes?.write(gradS, false)
            velocityRes?.swap()
        }

        GLES30.glBindVertexArray(0)
    }

    /** Renders the final fluid simulation combined with HDR bloom post-processing to the screen. */
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

        // Pass 1: Render density to chromatic HDR scene
        shader.use()
        bindTexture(0, density.readTexture)
        shader.setUniform("uDensity", 0)
        shader.setUniform("uBandsLow", low0, low1, low2)
        shader.setUniform("uBandsHigh", high0, high1, high2)
        scene.write(shader, false)

        // Pass 2: Bright-pass prefilter for bloom
        prefilterShader.use()
        bindTexture(0, scene.texture)
        prefilterShader.setUniform("uSource", 0)
        prefilterShader.setUniform("uThreshold", (0.68f - avgEnergy * 0.2f).coerceIn(0.38f, 0.75f))
        prefilter.write(prefilterShader, false)

        // Pass 3: Separable blur (horizontal and vertical passes)
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

        // Pass 4: Composite scene and bloom onto the display framebuffer
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

    /** Binds a texture ID to the specified texture unit. */
    private fun bindTexture(unit: Int, textureId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
    }

    /** Rebuilds post-processing framebuffers when the surface dimensions change. */
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

    /** Releases all GPU resources, shaders, and FBOs. */
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
        currentGlContextHash.set(0)
    }
}
