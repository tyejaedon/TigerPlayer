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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.sin

/**
 * THE SUPREME GPU VORTEX: FluidRenderer
 * Optimized for 16-bit Float precision and high-dynamic range audio reactivity.
 */
@Singleton
class FluidRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : GLSurfaceView.Renderer {

    data class NodeField(val x: Float, val y: Float, val intensity: Float)

    data class ReactiveMotionFrame(
        val expansion: Float = 0f,
        val flowSpeed: Float = 1f,
        val turbulence: Float = 0f
    ) {
        companion object {
            fun fromAudio(frame: AudioReactiveFrame): ReactiveMotionFrame {
                val bass = frame.bass.coerceIn(0f, 1f)
                val treble = frame.treble.coerceIn(0f, 1f)
                val energy = frame.energy.coerceIn(0f, 1f)
                val flux = frame.flux.coerceIn(0f, 1f)

                // Exaggerated pulse: flux spikes push outward, low energy contracts inward.
                val fluxImpulse = flux.pow(1.35f) * 0.95f
                val energyContraction = (1f - energy).pow(1.2f) * 0.45f
                val expansion = (fluxImpulse - energyContraction).coerceIn(-0.55f, 0.95f)

                // Bass controls current speed from heavy/slow to fast/aggressive.
                val flowSpeed = (0.35f + bass.pow(1.6f) * 4.8f).coerceIn(0.25f, 5.5f)

                // Treble introduces micro-chaotic edge shimmer.
                val turbulence = (treble.pow(1.3f) * 1.75f).coerceIn(0f, 2.2f)

                return ReactiveMotionFrame(
                    expansion = expansion,
                    flowSpeed = flowSpeed,
                    turbulence = turbulence
                )
            }
        }
    }

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
    private val targetReactiveMotion = AtomicReference(ReactiveMotionFrame())

    private var smoothedReactiveMotion = ReactiveMotionFrame()
    private val smoothingTimeSeconds = 0.18f

    // FBO Resources
    private var velocityRes: PingPongBuffer? = null
    private var densityRes: PingPongBuffer? = null
    private var pressureRes: PingPongBuffer? = null
    private var divergenceRes: FrameBuffer? = null
    private var curlRes: FrameBuffer? = null
    private var bloomPrefilterRes: FrameBuffer? = null
    private var bloomPingPong: PingPongBuffer? = null

    private var advectionShader: Shader? = null
    private var divergenceShader: Shader? = null
    private var pressureShader: Shader? = null
    private var gradientSubtractShader: Shader? = null
    private var curlShader: Shader? = null
    private var vorticityShader: Shader? = null
    private var splatShader: Shader? = null
    private var bloomPrefilterShader: Shader? = null
    private var bloomBlurShader: Shader? = null
    private var displayShader: Shader? = null

    private var quadVao: Int = 0
    private var quadVbo: Int = 0
    private var lastFrameTime = System.nanoTime()

    // Bass -> warm red/violet, Treble -> sharp cyan/blue/white.
    private val chromaBandColors = arrayOf(
        floatArrayOf(1.52f, 0.08f, 0.34f),
        floatArrayOf(1.05f, 0.06f, 0.95f),
        floatArrayOf(1.24f, 0.52f, 0.16f),
        floatArrayOf(0.20f, 1.18f, 0.96f),
        floatArrayOf(0.14f, 0.64f, 1.82f),
        floatArrayOf(0.88f, 1.36f, 2.30f)
    )

    fun updateEnergyField(bands: List<Float>) { currentEnergy.set(bands) }
    fun updateNodeField(nodes: List<NodeField>) { currentNodes.set(nodes) }
    fun updateTimelineEnergy(progress: Float, amp: Float) { currentTimeline.set(Pair(progress, amp)) }
    fun updateReactiveMotion(frame: ReactiveMotionFrame) {
        targetReactiveMotion.set(frame)
    }

    fun updateAudioReactiveFrame(frame: AudioReactiveFrame) {
        updateReactiveMotion(ReactiveMotionFrame.fromAudio(frame))
        currentEnergy.set(listOf(frame.bass, frame.mid, frame.treble, frame.energy, frame.flux, frame.centroid))
    }

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
        curlRes = FrameBuffer(simWidth, simHeight)
        bloomPrefilterRes = FrameBuffer(simWidth, simHeight)
        bloomPingPong = PingPongBuffer(simWidth, simHeight)

        advectionShader = Shader(FluidShaders.vert, FluidShaders.advectionFrag)
        divergenceShader = Shader(FluidShaders.vert, FluidShaders.divergenceFrag)
        pressureShader = Shader(FluidShaders.vert, FluidShaders.pressureFrag)
        gradientSubtractShader = Shader(FluidShaders.vert, FluidShaders.gradientSubtractFrag)
        curlShader = Shader(FluidShaders.vert, FluidShaders.curlFrag)
        vorticityShader = Shader(FluidShaders.vert, FluidShaders.vorticityFrag)
        splatShader = Shader(FluidShaders.vert, FluidShaders.splatFrag)
        bloomPrefilterShader = Shader(FluidShaders.vert, FluidShaders.bloomPrefilterFrag)
        bloomBlurShader = Shader(FluidShaders.vert, FluidShaders.bloomBlurFrag)
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
        val bands = getPerceptualBands()

        GLES30.glViewport(0, 0, simWidth, simHeight)
        GLES30.glBindVertexArray(quadVao)

        val bass = ((bands[0] + bands[1]) * 0.5f).coerceIn(0f, 1f)
        val treble = ((bands[4] + bands[5]) * 0.5f).coerceIn(0f, 1f)
        val phase = (System.nanoTime() / 1_000_000_000.0).toFloat() * 0.35f

        // Core low-end bloom gives kick drums an obvious central pulse.
        if (bass > 0.018f) {
            val radius = 0.058f + bass * 0.12f
            val corePower = (bass * 6.9f).coerceIn(0.45f, 7.1f)
            applySplat(
                0.5f,
                0.5f,
                radius,
                floatArrayOf(corePower * 0.62f, corePower * 0.05f, corePower * 0.28f),
                true
            )

            // Ring layering creates visible gradients around the kick pulse instead of white-core blowout.
            val ringAngle = phase * 1.25f
            val ringOffset = 0.11f + bass * 0.07f
            val ringRadius = (radius * 0.78f).coerceIn(0.038f, 0.11f)
            val ringPower = (bass * 7.6f).coerceIn(0.65f, 7.2f)
            val ringX1 = (0.5f + cos(ringAngle) * ringOffset).coerceIn(0.08f, 0.92f)
            val ringY1 = (0.5f + sin(ringAngle) * ringOffset).coerceIn(0.08f, 0.92f)
            val ringX2 = (0.5f - cos(ringAngle) * ringOffset).coerceIn(0.08f, 0.92f)
            val ringY2 = (0.5f - sin(ringAngle) * ringOffset).coerceIn(0.08f, 0.92f)
            applySplat(
                ringX1,
                ringY1,
                ringRadius,
                floatArrayOf(ringPower * 1.04f, ringPower * 0.20f, ringPower * 0.34f),
                true
            )
            applySplat(
                ringX2,
                ringY2,
                ringRadius,
                floatArrayOf(ringPower * 0.20f, ringPower * 0.62f, ringPower * 1.14f),
                true
            )

            val swirl = 4.0f + bands[2] * 12f
            applySplat(0.5f, 0.5f, radius * 1.22f, floatArrayOf(swirl, -swirl, 0f), false)
        }

        // Six-band chromatic ribbon injection around the center.
        for (i in 0 until 6) {
            val amplitude = bands[i].coerceIn(0f, 1f)
            if (amplitude < 0.012f) continue

            val t = amplitude * amplitude
            val angle = phase + i * 1.0471976f
            val orbit = 0.17f + i * 0.045f
            val x = (0.5f + cos(angle) * orbit).coerceIn(0.08f, 0.92f)
            val y = (0.5f + sin(angle) * orbit).coerceIn(0.08f, 0.92f)
            val radius = 0.04f + t * 0.115f

            val c = chromaBandColors[i]
            val gain = (2.3f + t * 9.2f).coerceIn(1.8f, 12.8f)
            applySplat(x, y, radius, floatArrayOf(c[0] * gain, c[1] * gain, c[2] * gain), true)

            val tangential = (2.4f + t * 11f)
            val vx = -sin(angle) * tangential
            val vy = cos(angle) * tangential
            applySplat(x, y, radius * 1.10f, floatArrayOf(vx, vy, 0f), false)
        }

        // Treble accent: small, sharp edge splashes with white-cyan emphasis.
        if (treble > 0.04f) {
            val edgeAngle = -phase * 1.9f
            val edgeX = (0.5f + cos(edgeAngle) * 0.34f).coerceIn(0.06f, 0.94f)
            val edgeY = (0.5f + sin(edgeAngle) * 0.34f).coerceIn(0.06f, 0.94f)
            val edgePower = (treble * 8.2f).coerceIn(0.95f, 8.4f)
            applySplat(edgeX, edgeY, 0.05f + treble * 0.09f, floatArrayOf(edgePower * 0.24f, edgePower * 0.94f, edgePower * 1.36f), true)

            val haloAngle = edgeAngle + 1.5707963f
            val haloRadius = 0.41f
            val haloX = (0.5f + cos(haloAngle) * haloRadius).coerceIn(0.05f, 0.95f)
            val haloY = (0.5f + sin(haloAngle) * haloRadius).coerceIn(0.05f, 0.95f)
            val haloPower = (treble * 4.8f).coerceIn(0.45f, 4.6f)
            applySplat(haloX, haloY, 0.045f + treble * 0.065f, floatArrayOf(haloPower * 0.18f, haloPower * 0.72f, haloPower * 1.5f), true)

            applySplat(edgeX, edgeY, 0.08f + treble * 0.10f, floatArrayOf(0f, 7f + treble * 16f, 0f), false)
        }
        GLES30.glBindVertexArray(0)
    }

    private fun getPerceptualBands(): FloatArray {
        val bands = currentEnergy.get()
        if (bands.size >= 6) {
            return FloatArray(6) { idx -> bands[idx].coerceIn(0f, 1f) }
        }

        // Fallback from reactive frame style payloads.
        val bass = bands.getOrNull(0)?.coerceIn(0f, 1f) ?: 0f
        val mid = bands.getOrNull(1)?.coerceIn(0f, 1f) ?: 0f
        val treble = bands.getOrNull(2)?.coerceIn(0f, 1f) ?: 0f
        val energy = bands.getOrNull(3)?.coerceIn(0f, 1f) ?: 0f
        val flux = bands.getOrNull(4)?.coerceIn(0f, 1f) ?: 0f
        val centroid = bands.getOrNull(5)?.coerceIn(0f, 1f) ?: 0.5f

        return floatArrayOf(
            (bass * 0.9f + energy * 0.1f).coerceIn(0f, 1f),
            (bass * 0.35f + mid * 0.65f).coerceIn(0f, 1f),
            (mid * 0.78f + energy * 0.22f).coerceIn(0f, 1f),
            (mid * (1f - centroid * 0.25f) + treble * 0.15f).coerceIn(0f, 1f),
            (treble * 0.78f + flux * 0.22f).coerceIn(0f, 1f),
            (treble * 0.58f + flux * 0.42f).coerceIn(0f, 1f)
        )
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

        val targetMotion = targetReactiveMotion.get()
        val smoothingAlpha = (1f - exp(-dt / smoothingTimeSeconds)).coerceIn(0f, 1f)
        smoothedReactiveMotion = ReactiveMotionFrame(
            expansion = lerp(smoothedReactiveMotion.expansion, targetMotion.expansion, smoothingAlpha),
            flowSpeed = lerp(smoothedReactiveMotion.flowSpeed, targetMotion.flowSpeed, smoothingAlpha),
            turbulence = lerp(smoothedReactiveMotion.turbulence, targetMotion.turbulence, smoothingAlpha)
        )

        advectionShader?.let { shader ->
            shader.use()
            shader.setUniform("uDt", dt)
            shader.setUniform("uFlowSpeed", smoothedReactiveMotion.flowSpeed)
            shader.setUniform("uExpansion", smoothedReactiveMotion.expansion)
            shader.setUniform("uTurbulence", smoothedReactiveMotion.turbulence)
            shader.setUniform("uTime", (System.nanoTime() / 1_000_000_000.0).toFloat())

            val kickBand = currentEnergy.get().getOrNull(0)?.coerceIn(0f, 1f) ?: 0f
            val kickPulse = kickBand * kickBand
            val velocityDissipation = (0.992f - kickPulse * 0.022f).coerceIn(0.955f, 0.995f)
            val densityDissipation = (0.985f - kickPulse * 0.014f).coerceIn(0.94f, 0.985f)

            // 🔥 FIX: Velocity Texture
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            shader.setUniform("uVelocity", 0)

            // Move Velocity
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", velocityDissipation)
            velocityRes?.write(shader, false)
            velocityRes?.swap()

            // Move Density
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, densityRes?.readTexture ?: 0)
            shader.setUniform("uSource", 1)
            shader.setUniform("uDissipation", densityDissipation)
            densityRes?.write(shader, false)
            densityRes?.swap()
        }

        // 1b. Vorticity confinement (preserves swirling micro-details)
        curlShader?.let { cShader ->
            cShader.use()
            cShader.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            cShader.setUniform("uVelocity", 0)
            curlRes?.write(cShader, false)
        }

        vorticityShader?.let { vortShader ->
            vortShader.use()
            vortShader.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())
            vortShader.setUniform("uDt", dt)
            val energy = currentEnergy.get().getOrNull(3)?.coerceIn(0f, 1f) ?: 0f
            vortShader.setUniform("uCurlStrength", 18f + energy * 24f)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            vortShader.setUniform("uVelocity", 0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curlRes?.texture ?: 0)
            vortShader.setUniform("uCurl", 1)

            velocityRes?.write(vortShader, false)
            velocityRes?.swap()
        }

        // 2. Projection (Divergence -> Pressure -> Gradient Subtract)
        divergenceShader?.let { divS ->
            divS.use()
            divS.setUniform("uExpansion", smoothedReactiveMotion.expansion)
            divS.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            divS.setUniform("uVelocity", 0)
            divergenceRes?.write(divS, false)
        }

        pressureShader?.let { presS ->
            presS.use()
            presS.setUniform("uExpansion", smoothedReactiveMotion.expansion)
            presS.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, divergenceRes?.texture ?: 0)
            presS.setUniform("uDivergence", 0)
            // 20 Iterations is the sweet spot for mobile (Stability vs Performance)
            repeat(20) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pressureRes?.readTexture ?: 0)
                presS.setUniform("uPressure", 1)
                pressureRes?.write(presS, false)
                pressureRes?.swap()
            }
        }

        gradientSubtractShader?.let { gradS ->
            gradS.use()
            gradS.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pressureRes?.readTexture ?: 0)
            gradS.setUniform("uPressure", 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, velocityRes?.readTexture ?: 0)
            gradS.setUniform("uVelocity", 1)
            velocityRes?.write(gradS, false)
            velocityRes?.swap()
        }

        GLES30.glBindVertexArray(0)
    }

    private fun renderToDisplay() {
        val shader = displayShader ?: return
        val density = densityRes ?: return

        // Lightweight half-float bloom chain: prefilter -> blurX -> blurY
        bloomPrefilterShader?.let { prefilter ->
            prefilter.use()
            prefilter.setUniform("uThreshold", 0.70f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, density.readTexture)
            prefilter.setUniform("uSource", 0)
            bloomPrefilterRes?.write(prefilter, false)
        }

        bloomBlurShader?.let { blur ->
            val pre = bloomPrefilterRes ?: return@let
            val bloom = bloomPingPong ?: return@let

            blur.use()
            blur.setUniform("uTexelSize", 1f / simWidth.toFloat(), 1f / simHeight.toFloat())

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pre.texture)
            blur.setUniform("uSource", 0)
            blur.setUniform("uDirection", 1f, 0f)
            bloom.write(blur, false)
            bloom.swap()

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloom.readTexture)
            blur.setUniform("uSource", 0)
            blur.setUniform("uDirection", 0f, 1f)
            bloom.write(blur, false)
            bloom.swap()
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader.use()

        // 🔥 FIX: Bind density to unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, density.readTexture)
        shader.setUniform("uDensity", 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomPingPong?.readTexture ?: 0)
        shader.setUniform("uBloom", 1)

        GLES30.glBindVertexArray(quadVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
    }
    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        listOf(velocityRes, densityRes, pressureRes, bloomPingPong).forEach { it?.release() }
        listOf(divergenceRes, curlRes, bloomPrefilterRes).forEach { it?.release() }
        listOf(
            advectionShader,
            divergenceShader,
            pressureShader,
            gradientSubtractShader,
            curlShader,
            vorticityShader,
            splatShader,
            bloomPrefilterShader,
            bloomBlurShader,
            displayShader
        ).forEach { it?.release() }
        currentGlContextHash = 0
    }

    private fun lerp(current: Float, target: Float, alpha: Float): Float {
        return current + (target - current) * alpha
    }
}