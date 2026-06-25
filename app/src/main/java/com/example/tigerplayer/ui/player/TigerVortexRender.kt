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
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// ============================================================================
// 1. TOP LAYER: COMPOSE WRAPPER
// ============================================================================

/**
 * A highly performant Compose wrapper for the OpenGL Fluid Vortex.
 * It passes the current audio amplitude to the GPU for live reaction.
 */
@Composable
fun FluidVortexRenderer(
    isPlaying: Boolean,
    amplitudes: List<Float>,
    audioReactive: AudioReactiveFrame,
    trackId: String,
    modifier: Modifier = Modifier
) {
    // Remember the renderer instance so we can update it without recreating it
    val renderer = remember { TigerVortexRenderer() }

    // Blend static waveform contour with live DSP features.
    val currentIntensity = remember(amplitudes, audioReactive.energy, audioReactive.flux) {
        val waveformAvg = if (amplitudes.isEmpty()) 0f else amplitudes.average().toFloat()
        ((waveformAvg * 0.5f) + (audioReactive.energy * 0.35f) + (audioReactive.flux * 0.15f)).coerceIn(0f, 1f)
    }

    val trackSeed = remember(trackId) {
        ((trackId.hashCode().toUInt().toLong() and 0xFFFFL).toFloat() / 65535f).coerceIn(0f, 1f)
    }

    // Pipe the intensity to the OpenGL thread
    LaunchedEffect(currentIntensity) {
        renderer.setTargetAmplitude(currentIntensity)
    }

    LaunchedEffect(audioReactive, trackSeed) {
        renderer.setReactiveFrame(audioReactive, trackSeed)
    }

    AndroidView(
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3) // Ensure OpenGL ES 3.0
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        modifier = modifier,
        update = { view ->
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

    // Handles to shader variables
    private var positionHandle = 0
    private var timeHandle = 0
    private var resolutionHandle = 0
    private var audioHandle = 0
    private var bandsHandle = 0
    private var energyHandle = 0
    private var fluxHandle = 0
    private var seedHandle = 0

    // State Variables
    private val startTime = System.nanoTime()
    private var width = 0f
    private var height = 0f

    // Audio Smoothing parameters
    private var currentAmplitude = 0f
    private var targetAmplitude = 0f
    @Volatile private var bass = 0f
    @Volatile private var mid = 0f
    @Volatile private var treble = 0f
    @Volatile private var energy = 0f
    @Volatile private var flux = 0f
    @Volatile private var seed = 0.5f

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
        this.targetAmplitude = amplitude * 3.0f // Scale up for visual impact
    }

    fun setReactiveFrame(frame: AudioReactiveFrame, trackSeed: Float) {
        bass = frame.bass
        mid = frame.mid
        treble = frame.treble
        energy = frame.energy
        flux = frame.flux
        seed = trackSeed
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

        // Get variable handles
        positionHandle = GLES30.glGetAttribLocation(program, "a_Position")
        timeHandle = GLES30.glGetUniformLocation(program, "u_time")
        resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
        audioHandle = GLES30.glGetUniformLocation(program, "u_audioData")
        bandsHandle = GLES30.glGetUniformLocation(program, "u_bands")
        energyHandle = GLES30.glGetUniformLocation(program, "u_energy")
        fluxHandle = GLES30.glGetUniformLocation(program, "u_flux")
        seedHandle = GLES30.glGetUniformLocation(program, "u_seed")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES30.glViewport(0, 0, w, h)
        width = w.toFloat()
        height = h.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        // 1. Calculate time
        val time = (System.nanoTime() - startTime) / 1_000_000_000.0f

        // 2. Smoothly interpolate audio amplitude to prevent jittering visually
        currentAmplitude += (targetAmplitude - currentAmplitude) * 0.15f

        // 3. Bind Uniforms
        GLES30.glUniform1f(timeHandle, time)
        GLES30.glUniform2f(resolutionHandle, width, height)
        GLES30.glUniform1f(audioHandle, currentAmplitude)
        GLES30.glUniform3f(bandsHandle, bass, mid, treble)
        GLES30.glUniform1f(energyHandle, energy)
        GLES30.glUniform1f(fluxHandle, flux)
        GLES30.glUniform1f(seedHandle, seed)

        // 4. Bind Attributes (Vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, 0)

        // 5. Draw Quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
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
// 3. BACKEND LAYER: GLSL SHADERS (Fractal Brownian Motion + Vortex)
// ============================================================================

object VortexShaders {

    // A simple pass-through vertex shader. 
    // Takes the quad coordinates and maps them to standard UVs.
    const val VERTEX_SHADER = """#version 300 es
        in vec4 a_Position;
        out vec2 v_uv;
        void main() {
            gl_Position = a_Position;
            v_uv = (a_Position.xy + 1.0) / 2.0;
        }
    """

    // A highly advanced fragment shader that generates a magical, fiery vortex
    // that reacts physically to the u_audioData uniform.
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
        
        // Pseudo-random hash function
        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }
        
        // 2D Noise
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
                mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), 
            u.y);
        }
        
        // Fractal Brownian Motion for the smoky/fluid texture
        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            vec2 shift = vec2(100.0);
            mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
            for (int i = 0; i < 5; ++i) {
                v += a * noise(p);
                p = rot * p * 2.0 + shift;
                a *= 0.5;
            }
            return v;
        }
        
        void main() {
            // Normalize coordinates and fix aspect ratio
            vec2 p = (v_uv - 0.5) * 2.0;
            p.x *= u_resolution.x / u_resolution.y;

            // Track-specific identity values derived from hash seed.
            float seedA = 0.75 + fract(u_seed * 13.7) * 0.65;
            float seedB = 0.55 + fract(u_seed * 29.3) * 0.70;
            float seedC = 0.65 + fract(u_seed * 47.9) * 0.50;
            
            float dist = length(p);
            float angle = atan(p.y, p.x);
            
            // Multi-band reactivity: bass drives spin radius, mid drives turbulence, treble drives sparkle.
            float bass = u_bands.x;
            float mid = u_bands.y;
            float treble = u_bands.z;
            float audioDistort = u_audioData * 1.2 + bass * 1.6 + u_flux * 0.9;
            float twirl = angle + u_time * (0.32 + bass * 0.55) - dist * (2.4 * seedA + audioDistort);
            vec2 twirlP = vec2(cos(twirl), sin(twirl)) * dist;
            
            // Domain warping using FBM
            vec2 q = vec2(0.0);
            q.x = fbm(twirlP * (1.1 + mid * 0.8) + 0.1 * u_time * seedB);
            q.y = fbm(twirlP + vec2(1.0 + u_seed));
            
            vec2 r = vec2(0.0);
            r.x = fbm(twirlP + 1.0 * q + vec2(1.7 + 5.0 * u_seed, 9.2) + (0.10 + treble * 0.22) * u_time);
            r.y = fbm(twirlP + 1.0 * q + vec2(8.3, 2.8 + 4.0 * u_seed) + (0.09 + mid * 0.18) * u_time);
            
            float f = fbm(twirlP + r);
            
            // Color Mapping (Igni Red & Dark Aard Blue mixed together)
            // Color base: Dark crimson to bright orange/red
            vec3 baseA = mix(vec3(0.03, 0.01, 0.06), vec3(0.12, 0.02, 0.01), seedC);
            vec3 baseB = mix(vec3(0.95, 0.22, 0.10), vec3(1.0, 0.55, 0.05), seedA);
            vec3 color = mix(baseA, baseB, f + bass * 0.2);
            color = mix(color, vec3(1.0, 0.55, 0.02), length(q) * (0.7 + mid * 0.8));
            
            // Flash white/blue based on audio intensity
            color = mix(color, vec3(0.8, 0.9, 1.0), length(r) * (u_audioData * 0.5 + treble * 0.7));
            
            // Fade out the edges softly
            float edgeMask = smoothstep(1.0, 0.3, dist);
            color *= edgeMask;
            
            // Glowing audio-reactive core
            float core = smoothstep(0.45 + bass * 0.35 + u_energy * 0.25, 0.0, dist);
            color += vec3(1.0, 0.2, 0.1) * core * (0.35 + u_energy + u_flux * 0.6);
            
            // Add a subtle vignette
            color *= 1.0 - 0.5 * pow(dist, 2.0);
            
            fragColor = vec4(color, 1.0);
        }
    """
}