# Mobile GPU Chip Architectures & Optimization Guide

This reference outlines the internal hardware architectures and platform-specific behaviors
of mobile graphics chips targeted by TigerPlayer.

---

## 1. Qualcomm Adreno (Snapdragon)

### Architecture: FlexRender & GMEM
- **GMEM:** Adreno GPUs feature fast on-chip memory (GMEM) dedicated to holding render target tiles (typically 256KB to 1MB+ depending on generation: Adreno 6xx/7xx).
- **FlexRender:** Dynamically switches between immediate-mode and tile-based rendering depending on scene complexity and memory footprint.
- **Key Bottlenecks:**
  - GMEM store/resolve: When an FBO pass finishes, GMEM contents are flushed to system DDR. Repeated FBO swaps without tile invalidation trigger massive memory bus power spikes.
  - Non-coalesced texture sampling in fragment shaders.

### Best Practices for Adreno:
1. Always call `glInvalidateFramebuffer` on intermediate FBOs before switching render targets.
2. Maintain power-of-two (POT) texture sizes ($128 \times 128$, $256 \times 256$) to align with GMEM tile cache line boundaries.
3. Prefer `mediump float` (16-bit FP) over `highp float`. Adreno ALUs process FP16 instructions at 2x throughput compared to FP32.

---

## 2. ARM Mali (MediaTek Dimensity / Samsung Exynos / Google Tensor)

### Architecture: Tile-Based Deferred Rendering (TBDR)
- **Tiling Engine:** The screen is subdivided into fixed $16 \times 16$ or $32 \times 32$ pixel tiles.
- **Tile Memory:** Color, depth, and stencil data for a tile reside in on-chip SRAM during shading.
- **Bifrost / Valhall / 5th Gen (Immortalis):** Strict deferred execution. Shaders do not begin fragment processing until all primitive geometry for that tile is binned.

### Best Practices for Mali:
1. **Avoid Transaction Elimination Hazards:** If an FBO is modified even slightly, Mali must write the entire tile back unless explicitly cleared or invalidated. Always use `glClear` at the start of a pass and `glInvalidateFramebuffer` at the end.
2. **Register Pressure:** Mali has a unified register file. Shaders with high register usage reduce thread occupancy per execution engine. Keep fragment shaders compact; split monolithic post-processing into multi-tap passes.
3. **Branch Divergence:** Branching inside fragment shaders (e.g., dynamic loops or divergent `if/else`) forces warp execution serialization. Use mathematical branchless formulations (`mix`, `clamp`, `step`).

---

## 3. Imagination PowerVR

### Architecture: Tile-Based Deferred Rendering with Hidden Surface Removal (HSR)
- Hardware HSR completely eliminates overdraw for opaque geometry prior to fragment execution.
- In post-processing and full-screen quad physics passes, HSR does not help because all pixels cover the same quad depth.
- Efficiency relies entirely on arithmetic density and minimizing texture cache misses during bilinear lookups.

---

## 4. Hardware Comparison & Format Compatibility Matrix

| Feature / Format | Qualcomm Adreno (6xx/7xx) | ARM Mali (Valhall / Immortalis) | PowerVR Rogue / Series 9 |
| :--- | :--- | :--- | :--- |
| **`GL_RGBA16F` Filter** | Hardware Linear | Hardware Linear | Hardware Linear |
| **`GL_R16F` Filter** | Hardware Linear | Hardware Linear | Hardware Linear |
| **FP16 ALU Rate** | 2x FP32 | 2x FP32 | 2x FP32 |
| **On-Chip Tile SRAM** | GMEM | Tile Buffer (Local) | Tile Memory |
| **Vulkan Compute Wave**| 64 or 128 threads | 16 or 32 threads | 32 threads |
| **Subpass Merging** | Supported | Highly Optimized | Supported |
