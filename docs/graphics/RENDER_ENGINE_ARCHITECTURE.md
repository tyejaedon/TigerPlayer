# TigerPlayer Graphics & Rendering Engine Architecture

## 1. Architectural Scope & Purpose

TigerPlayer incorporates a signature real-time GPU-accelerated graphics module providing interactive, audio-reactive visual dynamics (including `FluidVortexView`, `FluidRenderEngine`, and `GraphicsEngine`). The module simulates two-dimensional fluid mechanics via Eulerian Navier-Stokes equations and renders post-processed high-dynamic-range (HDR) visuals at target refresh rates of 60 to 120 FPS.

```
┌────────────────────────────────────────────────────────┐
│ Audio Thread (AdaptiveDspEngine @ 44.1 / 48 kHz)       │
│ - 6-Band Spectral Analysis (Sub-Bass to Presence)      │
│ - Transient Kick & Spectral Flux Detection             │
└───────────────────────────┬────────────────────────────┘
                            │ Thread Boundary (Confluent StateFlow / Atomic)
                            ▼
┌────────────────────────────────────────────────────────┐
│ UI / Rendering Loop (FluidRenderer @ 60 / 120 Hz)      │
│ - SurfaceView / GLSurfaceView (BLASTBufferQueue)       │
│ - OpenGL ES 3.0+ Context (Translucent RGBA8888)        │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │ Simulation Stage (Off-screen FBOs @ 128x128)       │ │
│ │ 1. Advection (Semi-Lagrangian backward trace)      │ │
│ │ 2. Audio Force & Dye Splatting                     │ │
│ │ 3. Vorticity Confinement (Curl recovery)           │ │
│ │ 4. Divergence Calculation                          │ │
│ │ 5. Pressure Poisson Solve (Jacobi iterations)      │ │
│ │ 6. Velocity Gradient Subtraction                   │ │
│ └─────────────────────────┬──────────────────────────┘ │
│                           │                            │
│ ┌─────────────────────────▼──────────────────────────┐ │
│ │ Post-Processing Stage (Full Screen Target)         │ │
│ │ 1. Dual-Filtering Bloom Prefilter (Threshold)      │ │
│ │ 2. Separable Gaussian Blur (Downsample / Upsample) │ │
│ │ 3. ACES Filmic Tone Mapping & Composition          │ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

---

## 2. Mathematical Foundation: Eulerian Fluid Dynamics

The fluid simulation is discretized over a staggered grid using the incompressible Navier-Stokes equations:

$$\frac{\partial \mathbf{u}}{\partial t} = -(\mathbf{u} \cdot \nabla)\mathbf{u} - \frac{1}{\rho}\nabla p + \nu \nabla^2 \mathbf{u} + \mathbf{f}$$

$$\nabla \cdot \mathbf{u} = 0$$

Where:
- $\mathbf{u} = (u, v)$ is the fluid velocity vector field.
- $p$ is the scalar pressure field.
- $\rho$ is fluid density.
- $\nu$ is kinematic viscosity.
- $\mathbf{f}$ represents external force injection (audio transients and touch inputs).

### Discrete Operator Stages

1. **Advection (Semi-Lagrangian):**
   $$q^{n+1}(\mathbf{x}) = q^n(\mathbf{x} - \Delta t \cdot \mathbf{u}(\mathbf{x}))$$
   Samples the previous field value at the back-traced location using bilinear texture interpolation.

2. **Vorticity Confinement:**
   Numerical dissipation dampens small-scale swirling eddies. Vorticity $\omega$ is computed as the 2D curl of velocity:
   $$\omega = \nabla \times \mathbf{u} = \frac{\partial v}{\partial x} - \frac{\partial u}{\partial y}$$
   Confinement force $\mathbf{f}_v = \epsilon (\mathbf{N} \times \omega)$ restores micro-swirls.

3. **Divergence:**
   Evaluates how much fluid is compressing or expanding at each grid cell:
   $$\nabla \cdot \mathbf{u} = \frac{u_{x+1} - u_{x-1}}{2\Delta x} + \frac{v_{y+1} - v_{y-1}}{2\Delta y}$$

4. **Pressure Poisson Equation (Jacobi Relaxation):**
   $$\nabla^2 p = \nabla \cdot \mathbf{u}$$
   Solved iteratively over 20 to 40 ping-pong passes:
   $$p_{i,j}^{k+1} = \frac{p_{i+1,j}^k + p_{i-1,j}^k + p_{i,j+1}^k + p_{i,j-1}^k - (\Delta x)^2 (\nabla \cdot \mathbf{u})_{i,j}}{4}$$

5. **Projection (Gradient Subtraction):**
   Enforces mass conservation ($\nabla \cdot \mathbf{u} = 0$):
   $$\mathbf{u}^{n+1} = \mathbf{u}^* - \nabla p$$

---

## 3. High-Dynamic-Range (HDR) Post-Processing & Tone Mapping

Color intensity values injected by heavy bass drops exceed $[0, 1]$ to simulate glowing neon plasma.
To display these intensities naturally without harsh digital clipping, a post-processing pipeline executes:

1. **Bloom Extraction:** High-pass filter selects luminance above $L_{th}$:
   $$C_{\text{bright}} = \max(C - L_{th}, 0)$$
2. **Separable Gaussian Blur:** Two-pass 1D convolution (horizontal then vertical) reduces sampling complexity from $O(N^2)$ to $O(2N)$:
   $$G(x) = \frac{1}{\sqrt{2\pi\sigma^2}} e^{-\frac{x^2}{2\sigma^2}}$$
3. **ACES Filmic Tone Mapping:** Compresses HDR range $[0, \infty)$ into LDR $[0, 1]$ preserving contrast and chromatic saturation:
   $$f(x) = \frac{x(2.51x + 0.03)}{x(2.43x + 0.59) + 0.14}$$

---

## 4. Audio-to-GPU Synchronization & Decoupling

A primary failure mode in multimedia apps is UI or audio thread stutter caused by coupling playback loops to rendering loops.

| Domain | Clock Rate | Execution Thread | Telemetry Delivery |
| :--- | :--- | :--- | :--- |
| **Audio Processing** | 44.1 / 48 kHz (1024-sample chunks = ~23.2ms) | High-Priority Audio Thread | Non-blocking `AtomicReference` / confluent `StateFlow` |
| **GPU Rendering** | 60 Hz (16.6ms) or 120 Hz (8.33ms) | GLSurfaceView Render Thread | Interpolated sampling of audio dynamics |

### Synchronization Rules:
1. The audio thread **never** acquires locks held by the rendering thread.
2. If frame rendering takes longer than the frame interval (e.g. thermal throttling), the audio thread continues uninterrupted.
3. The visualizer applies exponential smoothing filters to incoming audio metrics to eliminate visual jitter caused by disparate update frequencies.

---

## 5. Lifecycle Management & EGL Context Loss

Android may destroy the EGL context during configuration changes, app switching, or power conservation.
`FluidRenderer` must handle context loss gracefully:

1. On context loss, OpenGL texture IDs and FBOs become invalid.
2. `onSurfaceCreated()` detects context changes by monitoring `gl.hashCode()`.
3. Existing references are cleanly discarded, and all ping-pong buffers, shaders, and geometry VAOs are rebuilt.
4. On surface detachment (`onDetachedFromWindow()`), `renderer.release()` explicitly deletes GPU textures and framebuffers.
