# Vulkan Compute Pipeline Migration Blueprint

This reference details the transition path from OpenGL ES 3.0 fragment-shader simulation
to Vulkan 1.1+ Compute Shaders for TigerPlayer.

---

## 1. Why Vulkan for Audio-Reactive Simulations?

While OpenGL ES 3.0 handles fluid simulation through ping-pong Framebuffers and full-screen quad rasterization,
this approach incurs rasterizer overhead and raster state synchronization penalties.

**Key Vulkan Advantages:**
1. **Compute Shaders (`vkCmdDispatch`):** Navier-Stokes advection and Jacobi pressure solving execute directly on GPU compute queues without rasterization, vertex fetch, or scissor tests.
2. **Subpass Memory Coherency:** Multiple Jacobi iterations can run within on-chip tile memory using input attachments without writing intermediate fields to system RAM.
3. **Explicit Synchronization:** `VkSemaphore` and `VkPipelineBarrier` eliminate driver heuristics and hidden CPU-GPU pipeline bubbles.
4. **Zero-Copy Audio DSP Ingestion:** Audio frequency vectors from `AdaptiveDspEngine` stream directly into a mapped `VkBuffer` with `VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT`.

---

## 2. Architecture Comparison

### OpenGL ES 3.0 (Current Implementation)
- 1 Fullscreen quad geometry buffer.
- 10+ FBO render passes per frame.
- Ping-pong pointer swap after each draw call.
- Rasterizer active on every pass.

### Vulkan Architecture (Target Implementation)
- **Pipelines:**
  - `AdvectionComputePipeline`
  - `JacobiSolveComputePipeline`
  - `VorticityComputePipeline`
  - `BloomRasterPipeline` (Graphics queue)
  - `ToneMapRasterPipeline` (Swapchain present)
- **Workgroup Size:** `16 x 16 x 1` (256 invocations per workgroup, aligning with both Adreno and Mali wavefronts).
- **Descriptor Sets:** Storage Image bindings for reading/writing simulation grids directly.

---

## 3. Phased Migration Milestones

1. **Phase 1: Dual Backend Abstraction**
   - Introduce `RenderBackend` interface abstracting buffer swaps, shader execution, and surface binding.
   - Retain GLES3 backend as primary fallback.
2. **Phase 2: SPIR-V Shader Toolchain**
   - Author shaders in GLSL 4.50 compute format and compile to SPIR-V bytecode using `glslangValidator` or `shaderc`.
3. **Phase 3: Vulkan Compute Engine Implementation**
   - Implement native C++/NDK or Vulkan Java bindings for `VkInstance`, `VkDevice`, `VkQueue`, and `VkCommandBuffer`.
4. **Phase 4: Benchmarking & Profiling**
   - Compare frame time, battery draw, and thermal profile across representative test devices (Snapdragon 8 Gen 2/3, Dimensity 9200+, Tensor G3).
