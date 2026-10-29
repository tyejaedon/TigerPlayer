# Mobile GPU Optimization & Thermal Management

## 1. Mobile GPU Architecture Fundamentals

Unlike desktop discrete GPUs (which use immediate-mode rendering across wide memory buses), mobile System-on-Chips (SoCs) utilize **Tile-Based Deferred Rendering (TBDR)** to conserve energy and memory bus bandwidth:

- The render surface is partitioned into small rectangular tiles ($16 \times 16$ to $32 \times 32$ pixels).
- Primitives are binned into tiles during vertex processing.
- Rasterization, fragment shading, blending, and depth testing occur entirely within tiny, high-speed on-chip SRAM cache (GMEM on Adreno, Local Tile Buffer on Mali).
- Completed pixel tiles are written out to system LPDDR RAM only once shading for that tile is finished.

> [!IMPORTANT]
> External memory bus traffic (writing to and reading from LPDDR) consumes up to 100x more power than on-chip ALU arithmetic. Controlling memory bandwidth is the single most critical factor in preventing mobile device thermal throttling.

---

## 2. Platform-Specific Architectural Breakdown

### Qualcomm Adreno (Snapdragon SoCs)
- **Architecture Feature:** GMEM (General Memory) with FlexRender dynamic binning.
- **Critical Risk:** GMEM store-and-resolve stalls. Swapping between multiple Framebuffer Objects (FBOs) without notifying the driver forces GMEM to flush to DDR repeatedly.
- **Tuning Rule:** Call `glInvalidateFramebuffer()` on all intermediate scratch buffers (divergence, curl, bloom intermediate passes) so the driver can discard tile contents instead of flushing them to main memory.

### ARM Mali (MediaTek Dimensity, Exynos, Google Tensor)
- **Architecture Feature:** Strict Tile-Based Deferred Rendering with Transaction Elimination (TE).
- **Critical Risk:** Register spilling and tile buffer flushes. High register usage per fragment shader reduces warp occupancy.
- **Tuning Rule:** 
  - Never mix complex conditional branching within Jacobi pressure loops; maintain straight-line branchless arithmetic.
  - Always clear FBOs explicitly using `glClear(GL_COLOR_BUFFER_BIT)` at the start of a render pass to allow Mali's hardware to clear tile memory instantly.

### Imagination PowerVR
- **Architecture Feature:** Hardware Hidden Surface Removal (HSR) prior to fragment execution.
- **Note:** In full-screen quad simulations, HSR provides no benefit because geometry has identical depth ($Z=0$). Optimize by maximizing cache hits during bilinear texture taps.

---

## 3. Bandwidth Optimization Checklist

### 1. 16-Bit Half-Float Storage Standard
Always use half-precision floating-point formats for simulation textures:
- **Scalar fields (Pressure, Divergence, Curl):** `GLES30.GL_R16F` (internal) / `GLES30.GL_RED` (format) / `GLES30.GL_HALF_FLOAT` (type).
- **Vector fields (Velocity, Density):** `GLES30.GL_RGBA16F` (internal) / `GLES30.GL_RGBA` (format) / `GLES30.GL_HALF_FLOAT` (type).

| Texture Format | Bytes Per Pixel | Bandwidth @ 128x128 (30 Passes) | Relative Memory Traffic |
| :--- | :--- | :--- | :--- |
| `GL_RGBA32F` (Full Float) | 16 bytes | ~7.86 MB / frame | 200% (High Thermal Risk) |
| `GL_RGBA16F` (Half Float) | 8 bytes | ~3.93 MB / frame | 100% (Optimal Baseline) |
| `GL_R16F` (Scalar Half Float) | 2 bytes | ~0.98 MB / frame | 25% (Maximum Efficiency) |

### 2. Zero-Allocation Render Loop
Any object creation inside `onDrawFrame()` triggers garbage collector (GC) pauses on the Android runtime:
- Pre-allocate all `FloatBuffer`, `ByteBuffer`, `IntArray` instances during initialization.
- Cache all `glGetUniformLocation()` results in a lookup table during shader linking.
- Do not construct string concatenations, temporary Kotlin collections, or lambdas inside per-frame logic.

### 3. Shader Precision Qualifiers
- Declare `precision mediump float;` for color advection, dye dissipation, Gaussian blur, and bloom compositions.
- Use `precision highp float;` strictly for velocity advection, time integration, and Jacobi pressure solvers where numerical stability requires 24-bit mantissa precision.

---

## 4. Adaptive Thermal Throttling Mitigation

When a mobile device heats up, the kernel throttles GPU frequencies, leading to dropped frames and UI jank. TigerPlayer implements adaptive dynamic resolution scaling:

```kotlin
class AdaptiveFramePacingManager {
    private var averageFrameTimeMs = 16.0f
    var simulationResolution = 128 // Default: 128x128 grid
    var jacobiIterations = 30      // Default: 30 iterations

    fun reportFrameTime(frameDurationMs: Float) {
        averageFrameTimeMs = averageFrameTimeMs * 0.95f + frameDurationMs * 0.05f

        // High load detected: frame budget exceeded (e.g. > 14ms on 60Hz or > 7ms on 120Hz)
        if (averageFrameTimeMs > 14.0f && simulationResolution > 96) {
            simulationResolution = 96
            jacobiIterations = 20
        } else if (averageFrameTimeMs < 10.0f && simulationResolution < 128) {
            simulationResolution = 128
            jacobiIterations = 30
        }
    }
}
```

---

## 5. Profiling & Validation Toolchain

1. **Android GPU Inspector (AGI):**
   - Trace GPU system load, vertex/fragment processing times, and bandwidth counters (`GPU Memory Read/Write Bytes/sec`).
2. **RenderDoc for Android:**
   - Verify FBO binding targets, inspect attachment textures, and validate that transient framebuffers are discarded without extraneous memory stores.
3. **Systrace / Perfetto:**
   - Monitor frame pacing (`Choreographer#doFrame`) and ensure consistent 16.6ms (60 FPS) or 8.33ms (120 FPS) frame scheduling.
