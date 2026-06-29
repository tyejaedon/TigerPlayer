@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tigerplayer.engine.AudioReactiveFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

// ============================================================================
// 1. TOP LAYER: COMPOSE WRAPPER
// ============================================================================

/**
 * A highly performant Compose wrapper for the OpenGL Fluid Vortex.
 * Optimized for Samsung/Snapdragon BLASTBufferQueue lifecycles.
 */
@Composable
fun FluidVortexRenderer(
    isPlaying: Boolean,
    amplitudes: List<Float>,
    audioReactive: AudioReactiveFrame,
    trackId: String,
    modifier: Modifier = Modifier,
    isReducedComplexity: Boolean = false
) {
    // Remember the renderer instance so we can update it without recreating it
    val renderer = remember { TigerVortexRenderer() }

    // THE RELEASE RITUAL: Ensures GPU resources are freed when the player collapses
    DisposableEffect(Unit) {
        onDispose {
            renderer.release()
        }
    }

    // Blend static waveform contour with live DSP features.
    val currentIntensity = remember(amplitudes, audioReactive.energy, audioReactive.flux) {
        val waveformAvg = if (amplitudes.isEmpty()) 0f else amplitudes.average().toFloat()
        ((waveformAvg * 0.5f) + (audioReactive.energy * 0.35f) + (audioReactive.flux * 0.15f)).coerceIn(0f, 1f)
    }

    val trackSeed = remember(trackId) {
        ((trackId.hashCode().toUInt().toLong() and 0xFFFFL).toFloat() / 65535f).coerceIn(0f, 1f)
    }

    // Pipe parameters to the OpenGL thread
    LaunchedEffect(currentIntensity) {
        renderer.setTargetAmplitude(currentIntensity)
    }

    LaunchedEffect(audioReactive, trackSeed) {
        renderer.setReactiveFrame(audioReactive, trackSeed)
    }
    
    LaunchedEffect(isReducedComplexity) {
        renderer.setReducedComplexity(isReducedComplexity)
    }

    AndroidView(
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3) // Ensure OpenGL ES 3.0
                
                // PIP OPTIMIZATION: Ensure the surface is on top to prevent flicker
                if (isReducedComplexity) {
                    setZOrderOnTop(true)
                    holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                }
                
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier,
        update = { view ->
            // In PiP mode, we might want to keep the render loop active but desaturated
            // if isPlaying is false, but usually view.onPause() is fine.
            if (isPlaying) view.onResume() else view.onPause()
        }
    )
}

// ============================================================================
// 2. MIDDLE LAYER: OPENGL RENDERER
// ============================================================================

class TigerVortexRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var vbo = 0
    private var noiseTexture = 0

    // Handles to shader variables
    private var positionHandle = 0
    private var timeHandle = 0
    private var resolutionHandle = 0
    private var audioHandle = 0
    private var bandsHandle = 0
    private var energyHandle = 0
    private var fluxHandle = 0
    private var seedHandle = 0
    private var noiseHandle = 0
    private var complexityHandle = 0

    // State Variables
    private val startTime = System.nanoTime()
    private var width = 0f
    private var height = 0f
    private var isReducedComplexity = 0 // 0 for false, 1 for true

    // Audio Snapshots (Thread-Safe)
    private val reactiveData = AtomicReference(ReactiveSnapshot())
    private var currentAmplitude = 0f
    private var targetAmplitude = 0f

    data class ReactiveSnapshot(
        val bass: Float = 0f,
        val mid: Float = 0f,
        val treble: Float = 0f,
        val energy: Float = 0f,
        val flux: Float = 0f,
        val seed: Float = 0.5f
    )

    // A simple full-screen quad (two triangles)
    private val vertexData = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * 4) // 4 bytes per float
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertexData)
        .apply { position(0) }

    fun setTargetAmplitude(amplitude: Float) {
        // AUTO-UPSCALE: Ensure visuals are impactful even for quiet recordings
        val threshold = 0.25f
        val gain = if (amplitude < threshold) (threshold / amplitude.coerceAtLeast(0.01f)) else 1.0f
        this.targetAmplitude = (amplitude * gain * 3.5f).coerceAtMost(5.0f)
    }

    fun setReducedComplexity(reduced: Boolean) {
        this.isReducedComplexity = if (reduced) 1 else 0
    }

    fun setReactiveFrame(frame: AudioReactiveFrame, trackSeed: Float) {
        reactiveData.set(ReactiveSnapshot(
            bass = frame.bass,
            mid = frame.mid,
            treble = frame.treble,
            energy = frame.energy,
            flux = frame.flux,
            seed = trackSeed
        ))
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Compile and link shaders
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VortexShaders.VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, VortexShaders.FRAGMENT_SHADER)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        // Generate and bind Vertex Buffer Object
        val vboIds = IntArray(1)
        GLES30.glGenBuffers(1, vboIds, 0)
        vbo = vboIds[0]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        // NOISE GENERATION: Pre-compute noise to save GPU cycles
        noiseTexture = generateNoiseTexture()

        // Get variable handles
        positionHandle = GLES30.glGetAttribLocation(program, "a_Position")
        timeHandle = GLES30.glGetUniformLocation(program, "u_time")
        resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
        audioHandle = GLES30.glGetUniformLocation(program, "u_audioData")
        bandsHandle = GLES30.glGetUniformLocation(program, "u_bands")
        energyHandle = GLES30.glGetUniformLocation(program, "u_energy")
        fluxHandle = GLES30.glGetUniformLocation(program, "u_flux")
        seedHandle = GLES30.glGetUniformLocation(program, "u_seed")
        noiseHandle = GLES30.glGetUniformLocation(program, "u_noiseTex")
        complexityHandle = GLES30.glGetUniformLocation(program, "u_reducedComplexity")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES30.glViewport(0, 0, w, h)
        width = w.toFloat().coerceAtLeast(1f)
        height = h.toFloat().coerceAtLeast(1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val snapshot = reactiveData.get()
        val time = (System.nanoTime() - startTime) / 1_000_000_000.0f

        // SLOWER REACTION: Reduced interpolation factor (0.15f -> 0.08f) to add "weight" to visuals
        currentAmplitude += (targetAmplitude - currentAmplitude) * 0.08f

        // Bind Uniforms
        GLES30.glUniform1f(timeHandle, time)
        GLES30.glUniform2f(resolutionHandle, width, height)
        GLES30.glUniform1f(audioHandle, currentAmplitude)
        GLES30.glUniform3f(bandsHandle, snapshot.bass, snapshot.mid, snapshot.treble)
        GLES30.glUniform1f(energyHandle, snapshot.energy)
        GLES30.glUniform1f(fluxHandle, snapshot.flux)
        GLES30.glUniform1f(seedHandle, snapshot.seed)
        GLES30.glUniform1i(complexityHandle, isReducedComplexity)

        // Bind Noise Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, noiseTexture)
        GLES30.glUniform1i(noiseHandle, 0)

        // Bind Attributes
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun generateNoiseTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val size = 64
        val pixels = ByteBuffer.allocateDirect(size * size)
        val random = Random(42)
        repeat(size * size) {
            pixels.put(random.nextInt(256).toByte())
        }
        pixels.position(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, size, size, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, pixels)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        
        return textures[0]
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        if (noiseTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(noiseTexture), 0)
            noiseTexture = 0
        }
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e("TigerVortexRenderer", "Error compiling shader: $error")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}

// ============================================================================
// 3. BACKEND LAYER: GLSL SHADERS (Optimized FBM + Tonemapping)
// ============================================================================

object VortexShaders {

    const val VERTEX_SHADER = """#version 300 es
        in vec4 a_Position;
        out vec2 v_uv;
        void main() {
            gl_Position = a_Position;
            v_uv = (a_Position.xy + 1.0) / 2.0;
        }
    """

    const val FRAGMENT_SHADER = """#version 300 es
        precision highp float;
        
        in vec2 v_uv;
        out vec4 fragColor;
        
        uniform float u_time;
        uniform vec2 u_resolution;
        uniform float u_audioData;
        uniform vec3 u_bands;
        uniform float u_energy;
        uniform float u_flux;
        uniform float u_seed;
        uniform sampler2D u_noiseTex;
        uniform int u_reducedComplexity;
        
        // ACES Filmic Tone Mapping: Preserves color identity at high intensities
        vec3 ACESFilm(vec3 x) {
            float a = 2.51; float b = 0.03; float c = 2.43; float d = 0.59; float e = 0.14;
            return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
        }

        // Texture-based Noise (Cheaper than procedural hash)
        float noise(vec2 p) {
            return texture(u_noiseTex, p).r;
        }
        
        // Optimized FBM using Noise Texture
        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
            int octaves = (u_reducedComplexity == 1) ? 2 : 4;
            for (int i = 0; i < octaves; ++i) {
                v += a * noise(p);
                p = rot * p * 2.02;
                a *= 0.5;
            }
            return v;
        }
        
        void main() {
            vec2 p = (v_uv - 0.5) * 2.0;
            p.x *= u_resolution.x / u_resolution.y;

            float seedA = 0.75 + fract(u_seed * 13.7) * 0.65;
            float seedB = 0.55 + fract(u_seed * 29.3) * 0.70;
            float seedC = 0.65 + fract(u_seed * 47.9) * 0.50;
            
            float dist = length(p);
            float angle = atan(p.y, p.x);
            
            float bass = u_bands.x;
            float mid = u_bands.y;
            float treble = u_bands.z;
            
            // BEAT-DRIVEN PHYSICS: Use u_flux to trigger visual transients
            float onset = smoothstep(0.4, 0.9, u_flux);
            float audioDistort = u_audioData * 1.5 + bass * 2.0 + u_energy * 0.8;
            
            float twirl = angle + u_time * (0.3 + bass * 0.5) - dist * (2.2 * seedA + audioDistort);
            vec2 twirlP = vec2(cos(twirl), sin(twirl)) * dist;
            
            // Domain warping using Texture-FBM
            vec2 q = vec2(0.0);
            q.x = fbm(twirlP * (1.0 + mid * 0.5) + 0.05 * u_time * seedB);
            q.y = fbm(twirlP + vec2(u_seed));
            
            vec2 r = vec2(0.0);
            r.x = fbm(twirlP + 1.2 * q + vec2(1.7, 9.2) + (0.1 + treble * 0.2) * u_time);
            r.y = fbm(twirlP + 1.2 * q + vec2(8.3, 2.8) + (0.08 + mid * 0.15) * u_time);
            
            float f = fbm(twirlP + r);
            
            // Base color palette
            vec3 baseA = mix(vec3(0.02, 0.01, 0.05), vec3(0.1, 0.02, 0.01), seedC);
            vec3 baseB = mix(vec3(0.9, 0.2, 0.1), vec3(1.0, 0.5, 0.0), seedA);
            vec3 color = mix(baseA, baseB, f + bass * 0.3);
            
            // Energy flashes on beat (onset)
            color = mix(color, vec3(1.0, 0.6, 0.1), length(q) * (0.6 + mid * 0.7 + onset * 0.5));
            color = mix(color, vec3(0.7, 0.8, 1.0), length(r) * (u_audioData * 0.6 + treble * 0.9 + onset * 0.4));
            
            float edgeMask = smoothstep(1.1, 0.2, dist);
            color *= edgeMask;
            
            // Beat-reactive glowing core
            float coreSize = 0.4 + bass * 0.4 + u_energy * 0.2 + onset * 0.15;
            float core = smoothstep(coreSize, 0.0, dist);
            color += vec3(1.0, 0.3, 0.1) * core * (0.4 + u_energy * 1.2 + u_flux * 0.8);
            
            // Tonemapping Pass (Studio Standard)
            color = ACESFilm(color * 1.2);
            
            // Vignette
            color *= 1.0 - 0.6 * pow(dist, 2.0);
            
            fragColor = vec4(color, 1.0);
        }
    """
}
