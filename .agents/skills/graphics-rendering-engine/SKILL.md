---
name: graphics-rendering-engine
description: >-
  Actionable procedures, shader authoring, GPU profiling, and render-loop optimization
  for TigerPlayer's OpenGL ES 3.0/3.2 and Vulkan graphics engine. Use when developing,
  optimizing, or debugging shaders, FBOs, ping-pong passes, mobile GPU hardware issues
  (Adreno, Mali, PowerVR), or audio-reactive graphics pipelines.
---

# Graphics & Rendering Engine Skill

This skill provides step-by-step procedures, diagnostic workflows, and performance guardrails
for engineering high-performance (60/120 FPS) graphics, fluid dynamics, and shaders in TigerPlayer.

---

## 1. Core Rendering Pipeline Architecture

TigerPlayer uses an off-screen Eulerian fluid simulation grid combined with post-processing:

```
[AdaptiveDspEngine] ──> AudioReactiveFrame (Atomic StateFlow)
                              │
                              ▼
[FluidRenderer: onDrawFrame] ──> Delta Time & Force Injection
                              │
  ┌───────────────────────────┴─────────────────────────────┐
  ▼                                                         ▼
[Advection Pass] (PingPongBuffer: velocityRes)         [Splat / Swirl Pass]
  │                                                         │
  ▼                                                         ▼
[Vorticity & Curl Calculation] (FrameBuffer: curlRes)  [Divergence Pass]
  │                                                         │
  └───────────────────────────┬─────────────────────────────┘
                              ▼
  [Pressure Poisson Jacobi Solve (20-40 iterations)] (PingPongBuffer: pressureRes)
                              │
                              ▼
  [Gradient Subtraction & Velocity Projection]
                              │
                              ▼
  [Density / Dye Advection Pass] (PingPongBuffer: densityRes)
                              │
                              ▼
  [HDR Scene Composite & Bloom Downsample/Upsample] (Separable Gaussian)
                              │
                              ▼
  [Tone Mapping (ACES Filmic) & Final Blit to SurfaceView]
```

---

## 2. Standardized Shader Validation Procedure

Always validate shader compilation and program linking status. Unchecked shader compilation
silently falls back to black screens or undefined behavior on mobile GPU drivers.

### Robust Shader Compilation Pattern (Kotlin / GLES30)

```kotlin
fun compileShader(type: Int, source: String): Int {
    val shaderId = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shaderId, source)
    GLES30.glCompileShader(shaderId)

    val compileStatus = IntArray(1)
    GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
    if (compileStatus[0] == 0) {
        val infoLog = GLES30.glGetShaderInfoLog(shaderId)
        GLES30.glDeleteShader(shaderId)
        throw IllegalStateException("Shader compilation failed (${if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"}):\n$infoLog")
    }
    return shaderId
}

fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
    val programId = GLES30.glCreateProgram()
    GLES30.glAttachShader(programId, vertexShader)
    GLES30.glAttachShader(programId, fragmentShader)
    GLES30.glLinkProgram(programId)

    val linkStatus = IntArray(1)
    GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
        val infoLog = GLES30.glGetProgramInfoLog(programId)
        GLES30.glDeleteProgram(programId)
        throw IllegalStateException("Shader program linking failed:\n$infoLog")
    }
    return programId
}
```

---

## 3. Mobile GPU Optimization Playbook

Mobile GPUs (Qualcomm Adreno, ARM Mali, PowerVR) are **Tile-Based Deferred Renderers (TBDR)**.
Unlike desktop GPUs, external memory bandwidth (writing to and reading from system LPDDR)
is the primary bottleneck and thermal trigger, not raw ALU computation.

### Optimization 1: Use 16-Bit Half-Float Textures
- **Scalar fields (Pressure, Divergence, Curl):** Use `internalFormat = GLES30.GL_R16F`, `format = GLES30.GL_RED`, `type = GLES30.GL_HALF_FLOAT`.
- **Vector fields (Velocity, Density):** Use `internalFormat = GLES30.GL_RGBA16F`, `format = GLES30.GL_RGBA`, `type = GLES30.GL_HALF_FLOAT`.
- **Avoid 32-bit float (`GL_RGBA32F`):** Halves bandwidth, enables hardware linear filtering on all mobile GPUs, and eliminates memory cache thrashing.

### Optimization 2: FBO Invalidation & Tile Discard
Before reusing or releasing transient scratch buffers (such as curl, divergence, or ping-pong intermediate iterations), notify the driver to discard on-chip tile memory without writing back to DDR:
```kotlin
fun invalidateFramebuffer(fbo: Int, attachment: Int = GLES30.GL_COLOR_ATTACHMENT0) {
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
    val attachments = intArrayOf(attachment)
    GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, attachments, 0)
}
```

### Optimization 3: Zero-Allocation `onDrawFrame`
- Never instantiate Kotlin objects, arrays, lambdas, or `String` keys inside the render loop.
- Pre-cache all uniform locations during initialization via `glGetUniformLocation`.
- Store full-screen quad vertex buffers in direct native memory via `ByteBuffer.allocateDirect()`.

---

## 4. Hardware Reference & Migration Blueprints

For in-depth microarchitecture and migration strategies, refer to:
- [Mobile GPU Chip Architectures & Optimization Reference](./references/mobile-gpu-chips.md)
- [Vulkan Compute Pipeline Migration Blueprint](./references/vulkan-migration.md)

---

## 5. Diagnostics & Profiling Checklist

1. **Android GPU Inspector (AGI):** Profile system trace, GPU counter tracks, and frame slicing to detect GPU bound vs CPU bound bottlenecks.
2. **RenderDoc for Android:** Capture full frame passes to inspect texture attachments, uniform values, and shader assembly.
3. **Thermal Throttling Safeguards:** Implement adaptive downscaling. If frame time exceeds 14ms (on 60Hz) or 7ms (on 120Hz), reduce the fluid simulation grid from $128 \times 128$ to $96 \times 96$ or scale Jacobi iterations from 30 to 18.
