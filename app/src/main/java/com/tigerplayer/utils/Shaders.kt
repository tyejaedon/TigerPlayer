package com.tigerplayer.utils

/**
 * 🌊 High-Performance GLSL 3.0 Shaders for Navier-Stokes Fluid Dynamics
 * Optimized for TigerPlayer's FluidVortex visualizer.
 *
 * Provides GPU-accelerated GLSL ES 3.0 shader source code for:
 * - Eulerian fluid simulation (advection, divergence, pressure Poisson solve, velocity projection)
 * - Vorticity confinement and curl calculation for micro-swirl restoration
 * - Audio-driven splatting and chromatic energy steering
 * - Post-processing pipeline (ACES filmic tone mapping, multi-tap Gaussian bloom, and composition)
 */
object FluidShaders {

    /**
     * 📐 Fullscreen Quad Vertex Shader
     *
     * **Objective:** Projects a screen-aligned quad geometry covering Normalized Device Coordinates (NDC) [-1, 1]
     * and maps it to normalized texture coordinates [0, 1].
     *
     * **Attributes:**
     * - `aPosition` (in vec2): Vertex position attribute in NDC space [-1, 1].
     *
     * **Outputs:**
     * - `vUv` (out vec2): Interpolated UV texture coordinate range [0.0, 1.0].
     * - `gl_Position` (out vec4): Final vertex position in NDC [-1..1, -1..1, 0, 1].
     */
    const val vert = """
        #version 300 es
        layout(location = 0) in vec2 aPosition;
        out vec2 vUv;
        void main() {
            // Standardizes mapping: if aPosition is -1 to 1, vUv becomes 0 to 1
            vUv = aPosition * 0.5 + 0.5;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    /**
     * 🌊 Fluid Advection Fragment Shader
     *
     * **Objective:** Transports fluid velocity and density/source fields backward along the velocity vector field
     * (Semi-Lagrangian advection step in Navier-Stokes equations). Incorporates audio-driven kick impulses, radial expansion,
     * and procedural noise turbulence.
     *
     * **Uniforms:**
     * - `uVelocity` (sampler2D): Velocity field texture (x,y vector components).
     * - `uSource` (sampler2D): Field to be advected (velocity, color, or density).
     * - `uDt` (float): Delta time / timestep scale.
     * - `uKick` (float): Audio transient/kick impulse energy level [0..1].
     * - `uDissipation` (float): Decay factor per frame (e.g. 0.98 for subtle dissipation).
     * - `uExpansion` (float): Outward/inward radial expansion factor.
     * - `uFlowSpeed` (float): Global fluid movement speed factor.
     * - `uTurbulence` (float): Multiplier for procedural noise turbulence field.
     * - `uTime` (float): Animation timestamp in seconds for continuous noise evolution.
     *
     * **GLSL Functions:**
     * - `float hash(vec2 p)`:
     *     - Objective: Generates deterministic pseudo-random float value from 2D coordinates.
     *     - Inputs: `p` (vec2) - Input coordinate seed.
     *     - Probable Return: `float` in range [0.0, 1.0].
     * - `float noise(vec2 p)`:
     *     - Objective: Calculates smooth 2D value noise via bilinear interpolation of hashed grid points.
     *     - Inputs: `p` (vec2) - Sample position.
     *     - Probable Return: `float` in range [0.0, 1.0].
     * - `vec2 turbulenceField(vec2 uv, float time)`:
     *     - Objective: Constructs a rotational velocity disturbance field driven by noise and audio turbulence.
     *     - Inputs: `uv` (vec2) - Texture coordinate, `time` (float) - Current time in seconds.
     *     - Probable Return: `vec2` directional velocity vector offset.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Advected and dissipated field value (RGBA vector).
     */
    const val advectionFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        uniform sampler2D uSource;
        uniform float uDt;
        uniform float uKick;
        uniform float uDissipation;
        uniform float uExpansion;
        uniform float uFlowSpeed;
        uniform float uTurbulence;
        uniform float uTime;

        in vec2 vUv;
        out vec4 outColor;

        // Objective: Generates pseudo-random float noise value from a 2D position vector.
        // Return: Normalized float value in range [0.0, 1.0].
        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

        // Objective: Computes smooth 2D value noise using bilinear interpolation.
        // Return: Interpolated noise float value in range [0.0, 1.0].
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
                mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
                u.y
            );
        }

        // Objective: Constructs procedural curl turbulence force vector from noise fields and audio.
        // Return: 2D velocity offset vector scaled by turbulence strength.
        vec2 turbulenceField(vec2 uv, float time) {
            float n1 = noise(uv * 18.0 + vec2(time * 0.95, -time * 0.73));
            float n2 = noise(uv * 26.0 + vec2(-time * 1.11, time * 0.67));
            vec2 v = vec2(n1 - 0.5, n2 - 0.5);
            float invLen = inversesqrt(max(dot(v, v), 1e-4));
            return v * invLen * uTurbulence * 0.22;
        }

        void main() {
            // Back-trace the velocity to find the source of the fluid 'ink'
            vec2 vel = texture(uVelocity, vUv).xy * uFlowSpeed;
            float kickPulse = smoothstep(0.08, 0.92, uKick);
            vel += turbulenceField(vUv, uTime) * (1.0 + kickPulse * 0.85);

            vec2 coord = vUv - uDt * vel;

            // Expansion > 0 pushes the field outward, < 0 contracts inward.
            vec2 centered = coord - 0.5;
            vec2 radialKick = normalize(centered + vec2(1e-5)) * (kickPulse * 0.018);
            coord = (centered - radialKick) / (1.0 + uExpansion + kickPulse * 0.06) + 0.5;
            coord = clamp(coord, vec2(0.001), vec2(0.999));

            float kickDissipation = mix(1.0, 0.96, kickPulse);
            outColor = texture(uSource, coord) * (uDissipation * kickDissipation);
        }
    """

    /**
     * 🎈 Pressure Poisson Solver Fragment Shader
     *
     * **Objective:** Performs one Jacobi iteration to solve the pressure Poisson equation
     * (`∇²P = ∇·V`), resolving fluid pressure to enforce incompressibility.
     *
     * **Uniforms:**
     * - `uPressure` (sampler2D): Pressure field texture from previous iteration (scalar stored in .x).
     * - `uDivergence` (sampler2D): Computed velocity divergence field texture (scalar stored in .x).
     * - `uExpansion` (float): Expansion parameter softening pressure compression.
     * - `uTexelSize` (vec2): Dimension of a single texel in UV coordinates (`1/width`, `1/height`).
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Updated pressure scalar value written to `.x` channel.
     */
    const val pressureFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uPressure;
        uniform sampler2D uDivergence;
        uniform float uExpansion;
        uniform vec2 uTexelSize;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            float pL = texture(uPressure, vUv - vec2(uTexelSize.x, 0.0)).x;
            float pR = texture(uPressure, vUv + vec2(uTexelSize.x, 0.0)).x;
            float pB = texture(uPressure, vUv - vec2(0.0, uTexelSize.y)).x;
            float pT = texture(uPressure, vUv + vec2(0.0, uTexelSize.y)).x;
            float div = texture(uDivergence, vUv).x;

            // "Breathing" pressure solve: expansion softly loosens compression.
            float breath = 1.0 + uExpansion * 0.55;
            float relax = 0.12 * abs(uExpansion);

            // Jacobi iteration for pressure calculation
            outColor = vec4((pL + pR + pB + pT - div * breath) / (4.0 + relax), 0.0, 0.0, 1.0);
        }
    """

    /**
     * 📐 Velocity Divergence Fragment Shader
     *
     * **Objective:** Calculates the divergence of the velocity field (`∇·V`), measuring net fluid flux in/out of each grid cell.
     *
     * **Uniforms:**
     * - `uVelocity` (sampler2D): Intermediate velocity field texture.
     * - `uExpansion` (float): Expansion modifier influencing net divergence flux.
     * - `uTexelSize` (vec2): Dimension of 1 texel in UV space (`1/width`, `1/height`).
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Scalar divergence value stored in `.x` channel.
     */
    const val divergenceFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        uniform float uExpansion;
        uniform vec2 uTexelSize;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            float vL = texture(uVelocity, vUv - vec2(uTexelSize.x, 0.0)).x;
            float vR = texture(uVelocity, vUv + vec2(uTexelSize.x, 0.0)).x;
            float vB = texture(uVelocity, vUv - vec2(0.0, uTexelSize.y)).y;
            float vT = texture(uVelocity, vUv + vec2(0.0, uTexelSize.y)).y;

            float breath = 1.0 + uExpansion * 0.65;

            outColor = vec4(0.5 * (vR - vL + vT - vB) * breath, 0.0, 0.0, 1.0);
        }
    """

    /**
     * 🧮 Gradient Subtraction (Velocity Projection) Fragment Shader
     *
     * **Objective:** Subtracts the pressure gradient from the velocity field (`V_new = V - ∇P`), yielding a mass-conserving,
     * divergence-free incompressible velocity field.
     *
     * **Uniforms:**
     * - `uPressure` (sampler2D): Calculated pressure field texture.
     * - `uVelocity` (sampler2D): Unconstrained velocity field texture.
     * - `uTexelSize` (vec2): Dimension of 1 texel in UV space.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Divergence-free velocity vector stored in `.xy` channels.
     */
    const val gradientSubtractFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uPressure;
        uniform sampler2D uVelocity;
        uniform vec2 uTexelSize;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            float pL = texture(uPressure, vUv - vec2(uTexelSize.x, 0.0)).x;
            float pR = texture(uPressure, vUv + vec2(uTexelSize.x, 0.0)).x;
            float pB = texture(uPressure, vUv - vec2(0.0, uTexelSize.y)).x;
            float pT = texture(uPressure, vUv + vec2(0.0, uTexelSize.y)).x;
            vec2 vel = texture(uVelocity, vUv).xy;

            // Subtract pressure gradient to ensure mass conservation (incompressibility)
            outColor = vec4(vel - vec2(pR - pL, pT - pB), 0.0, 1.0);
        }
    """

    /**
     * 🌀 Curl (Vorticity) Computation Fragment Shader
     *
     * **Objective:** Computes 2D rotational vorticity (`curl = ∂Vy/∂x - ∂Vx/∂y`) from adjacent velocity texels.
     *
     * **Uniforms:**
     * - `uVelocity` (sampler2D): Velocity field texture.
     * - `uTexelSize` (vec2): Dimension of 1 texel in UV space.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Scalar vorticity magnitude stored in `.x` channel.
     */
    const val curlFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        uniform vec2 uTexelSize;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            float L = texture(uVelocity, vUv - vec2(uTexelSize.x, 0.0)).y;
            float R = texture(uVelocity, vUv + vec2(uTexelSize.x, 0.0)).y;
            float B = texture(uVelocity, vUv - vec2(0.0, uTexelSize.y)).x;
            float T = texture(uVelocity, vUv + vec2(0.0, uTexelSize.y)).x;
            float curl = (R - L) - (T - B);
            outColor = vec4(curl, 0.0, 0.0, 1.0);
        }
    """

    /**
     * 🌪️ Vorticity Confinement Fragment Shader
     *
     * **Objective:** Applies a restorative rotational force to counteract numerical damping and recreate micro-vortices
     * and fine fluid swirls.
     *
     * **Uniforms:**
     * - `uVelocity` (sampler2D): Current velocity field texture.
     * - `uCurl` (sampler2D): Precomputed curl field texture.
     * - `uTexelSize` (vec2): Dimension of 1 texel in UV space.
     * - `uCurlStrength` (float): Vorticity confinement force scaling factor.
     * - `uDt` (float): Timestep factor.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Enhanced velocity field stored in `.xy` channels.
     */
    const val vorticityFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        uniform sampler2D uCurl;
        uniform vec2 uTexelSize;
        uniform float uCurlStrength;
        uniform float uDt;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            float L = abs(texture(uCurl, vUv - vec2(uTexelSize.x, 0.0)).x);
            float R = abs(texture(uCurl, vUv + vec2(uTexelSize.x, 0.0)).x);
            float B = abs(texture(uCurl, vUv - vec2(0.0, uTexelSize.y)).x);
            float T = abs(texture(uCurl, vUv + vec2(0.0, uTexelSize.y)).x);
            float C = texture(uCurl, vUv).x;

            vec2 force = 0.5 * vec2(R - L, T - B);
            float invLen = inversesqrt(max(dot(force, force), 1e-5));
            force *= invLen;
            force *= uCurlStrength * C;

            vec2 velocity = texture(uVelocity, vUv).xy;
            velocity += force * uDt;
            outColor = vec4(velocity, 0.0, 1.0);
        }
    """

    /**
     * 💥 Splat (Impulse Injection) Fragment Shader
     *
     * **Objective:** Injects Gaussian circular impulses of velocity or color density at touch or audio-triggered points,
     * correcting for target texture aspect ratio.
     *
     * **Uniforms:**
     * - `uTarget` (sampler2D): Target texture field (velocity or density).
     * - `uPoint` (vec2): Center coordinate of impulse in normalized UV space [0..1].
     * - `uColor` (vec3): Impulse payload (RGB color or velocity vector).
     * - `uRadius` (float): Gaussian spread radius of splat.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Combined base field and splat impulse.
     */
    const val splatFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uTarget;
        uniform vec2 uPoint; // 0..1 coordinate
        uniform vec3 uColor;
        uniform float uRadius;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec3 base = texture(uTarget, vUv).rgb;
            vec2 res = vec2(textureSize(uTarget, 0));
            float aspect = res.x / res.y;

            // Correct for aspect ratio to keep splats circular
            vec2 uv = vUv;
            vec2 p = uPoint;
            uv.x *= aspect;
            p.x *= aspect;

            float d = distance(uv, p);
            float m = exp(-d * d / (uRadius * uRadius));

            outColor = vec4(base + uColor * m, 1.0);
        }
    """

    /**
     * 🎨 Display & Audio Spectral Post-Processing Fragment Shader
     *
     * **Objective:** Renders fluid density to screen using ACES filmic tone mapping, audio frequency spectral color steering,
     * peripheral neon halos, and dynamic alpha calculation.
     *
     * **Uniforms:**
     * - `uDensity` (sampler2D): HDR fluid density/color texture field.
     * - `uBandsLow` (vec3): Low-frequency audio spectrum energies (Bass).
     * - `uBandsHigh` (vec3): High-frequency audio spectrum energies (Treble).
     *
     * **GLSL Functions:**
     * - `vec3 ACESFilm(vec3 x)`:
     *     - Objective: Compresses High Dynamic Range (HDR) colors to low dynamic range [0, 1] using standard ACES film curve.
     *     - Inputs: `x` (vec3) - Raw HDR RGB color.
     *     - Probable Return: `vec3` compressed RGB color clamped to [0.0, 1.0].
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Final RGBA color with computed transparency.
     */
    const val displayFrag = """
    #version 300 es
    precision highp float;
    precision highp sampler2D;

    uniform sampler2D uDensity;
    uniform vec3 uBandsLow;
    uniform vec3 uBandsHigh;
    in vec2 vUv;
    out vec4 outColor;

    // Objective: ACES Filmic Tone Mapping: Compresses HDR colors for high-end cinematic displays.
    // Return: Clamped RGB color in range [0.0, 1.0].
    vec3 ACESFilm(vec3 x) {
        float a = 2.51; float b = 0.03; float c = 2.43; float d = 0.59; float e = 0.14;
        return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
    }

    void main() {
        vec3 rawColor = max(texture(uDensity, vUv).rgb, vec3(0.0));
        float lowEnergy = clamp((uBandsLow.r + uBandsLow.g + uBandsLow.b) / 3.0, 0.0, 1.0);
        float highEnergy = clamp((uBandsHigh.r + uBandsHigh.g + uBandsHigh.b) / 3.0, 0.0, 1.0);

        float peak = max(max(rawColor.r, rawColor.g), rawColor.b);
        float luma = dot(rawColor, vec3(0.2126, 0.7152, 0.0722));
        vec3 hue = rawColor / max(peak, 1e-4);
        float neutral = min(min(rawColor.r, rawColor.g), rawColor.b);
        vec3 chroma = max(rawColor - vec3(neutral), vec3(0.0));

        // Keep hue identity even when energy is high so the center does not wash to white.
        float intensity = 1.0 - exp(-peak * 1.6);
        vec3 color = chroma * (0.42 + intensity * 1.38) + hue * 0.07;

        // Soft highlight rolloff: compress overbright channels while retaining gradients.
        vec3 compressed = rawColor / (1.0 + rawColor * 0.85);
        color = mix(color, compressed, 0.48);

        // 6-band chromatic steering: low bands pull toward warm core, high bands toward neon edge.
        vec3 lowTint = vec3(
            1.00 + uBandsLow.r * 0.95,
            0.34 + uBandsLow.g * 0.74,
            0.20 + uBandsLow.b * 0.62
        );
        vec3 highTint = vec3(
            0.24 + uBandsHigh.r * 0.50,
            0.56 + uBandsHigh.g * 0.88,
            1.00 + uBandsHigh.b * 1.30
        );
        vec3 spectralTint = mix(lowTint, highTint, smoothstep(0.15, 0.92, highEnergy));
        float chromaDrive = clamp(0.22 + lowEnergy * 0.28 + highEnergy * 0.42, 0.0, 0.85);
        color = mix(color, color * spectralTint, chromaDrive);

        float dist = distance(vUv, vec2(0.5));

        // Peripheral neon halo to avoid flat fills and keep edge gradients alive.
        float outerHalo = smoothstep(0.18, 0.62, dist) * (1.0 - smoothstep(0.70, 1.02, dist));
        vec3 haloTint = vec3(0.22, 0.08, 0.24) * hue.r
            + vec3(0.06, 0.18, 0.16) * hue.g
            + vec3(0.08, 0.16, 0.34) * hue.b;
        color += haloTint * outerHalo * (0.24 + intensity * 0.32);

        // Prevent central overburn while keeping edge gradients vivid.
        float centerMask = 1.0 - smoothstep(0.04, 0.34, dist);
        float centerLimiter = mix(1.0, 0.70, centerMask * smoothstep(0.42, 1.0, peak));
        color *= centerLimiter;

        color = ACESFilm(color);
        color = pow(color, vec3(1.0 / 2.2));

        float vignette = 1.0 - smoothstep(0.40, 0.92, dist);
        color *= mix(0.64, 1.0, vignette);

        float alpha = clamp(intensity * 0.9 + luma * 0.35, 0.08, 0.95);

        outColor = vec4(color, alpha);
    }
"""

    /**
     * ✨ Bloom Threshold Prefilter Fragment Shader
     *
     * **Objective:** Isolates scene regions exceeding a luminance threshold to initialize the bloom glow pipeline.
     *
     * **Uniforms:**
     * - `uSource` (sampler2D): Source scene texture.
     * - `uThreshold` (float): Minimum luminance threshold for extraction.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Filtered bright-pass color RGBA.
     */
    const val bloomPrefilterFrag = """
        #version 300 es
        precision mediump float;
        precision mediump sampler2D;

        uniform sampler2D uSource;
        uniform float uThreshold;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec3 c = texture(uSource, vUv).rgb;
            float l = max(max(c.r, c.g), c.b);
            float k = max(l - uThreshold, 0.0) / max(l, 1e-4);
            outColor = vec4(c * k, 1.0);
        }
    """

    /**
     * 🌸 5-Tap Gaussian Bloom Blur Fragment Shader
     *
     * **Objective:** Performs a 5-tap linear directional Gaussian blur along `uDirection` for bloom glow expansion.
     *
     * **Uniforms:**
     * - `uSource` (sampler2D): Input texture to blur.
     * - `uTexelSize` (vec2): Dimension of 1 texel in UV space.
     * - `uDirection` (vec2): Blur direction unit vector (e.g. `(1,0)` for horizontal, `(0,1)` for vertical).
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Directionally blurred color RGBA.
     */
    const val bloomBlurFrag = """
        #version 300 es
        precision mediump float;
        precision mediump sampler2D;

        uniform sampler2D uSource;
        uniform vec2 uTexelSize;
        uniform vec2 uDirection;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec2 stepUv = uTexelSize * uDirection;
            vec3 sum = texture(uSource, vUv).rgb * 0.227027;
            sum += texture(uSource, vUv + stepUv * 1.384615).rgb * 0.316216;
            sum += texture(uSource, vUv - stepUv * 1.384615).rgb * 0.316216;
            sum += texture(uSource, vUv + stepUv * 3.230769).rgb * 0.070270;
            sum += texture(uSource, vUv - stepUv * 3.230769).rgb * 0.070270;
            outColor = vec4(sum, 1.0);
        }
    """

    /**
     * 🌫️ Generic Gaussian Blur Fragment Shader
     *
     * **Objective:** Applies a 5-tap linear Gaussian blur step driven by texel offset direction `uTexelDir`.
     *
     * **Uniforms:**
     * - `uTexture` (sampler2D): Texture field to blur.
     * - `uTexelDir` (vec2): Directional texel step offset (`uTexelSize * uDirection`).
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Blurred RGBA color output.
     */
    const val blurFrag = """
        #version 300 es
        precision mediump float;
        precision mediump sampler2D;

        uniform sampler2D uTexture;
        uniform vec2 uTexelDir;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec3 sum = texture(uTexture, vUv).rgb * 0.227027;
            sum += texture(uTexture, vUv + uTexelDir * 1.384615).rgb * 0.316216;
            sum += texture(uTexture, vUv - uTexelDir * 1.384615).rgb * 0.316216;
            sum += texture(uTexture, vUv + uTexelDir * 3.230769).rgb * 0.070270;
            sum += texture(uTexture, vUv - uTexelDir * 3.230769).rgb * 0.070270;
            outColor = vec4(sum, 1.0);
        }
    """

    /**
     * 🌟 Bloom Composite Fragment Shader
     *
     * **Objective:** Blends the sharp base scene texture with the blurred bloom glow field scaled by strength factor.
     *
     * **Uniforms:**
     * - `uScene` (sampler2D): Sharp base scene texture.
     * - `uBloom` (sampler2D): Blurred bloom glow texture.
     * - `uBloomStrength` (float): Multiplier for bloom glow contribution.
     *
     * **Inputs/Outputs:**
     * - `vUv` (in vec2): Interpolated UV coordinate.
     * - `outColor` (out vec4): Final combined scene and bloom RGBA color.
     */
    const val bloomCompositeFrag = """
        #version 300 es
        precision mediump float;
        precision mediump sampler2D;

        uniform sampler2D uScene;
        uniform sampler2D uBloom;
        uniform float uBloomStrength;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec3 scene = texture(uScene, vUv).rgb;
            vec3 bloom = texture(uBloom, vUv).rgb * max(uBloomStrength, 0.0);
            vec3 color = scene + bloom;
            outColor = vec4(color, 1.0);
        }
    """
}
