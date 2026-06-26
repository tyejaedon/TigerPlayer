package com.example.tigerplayer.utils

/**
 * 🌊 High-Performance GLSL 3.0 Shaders for Navier-Stokes Fluid Dynamics
 * Optimized for TigerPlayer's FluidVortex visualizer.
 *
 * FIXES:
 * 1. Robust UV mapping for fullscreen quads.
 * 2. Enhanced alpha visibility to prevent "Black Screen" transparency.
 * 3. Aspect-ratio corrected Splat logic.
 */
object FluidShaders {

    // Vertex shader: Projects a quad to cover the entire Normalized Device Coordinate space (-1 to 1)
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

    const val advectionFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;
        
        uniform sampler2D uVelocity;
        uniform sampler2D uSource;
        uniform float uDt;
        uniform float uDissipation;
        uniform float uKick;
        
        in vec2 vUv;
        out vec4 outColor;
        
        void main() {
            // Back-trace the velocity to find the source of the fluid ink.
            vec2 vel = texture(uVelocity, vUv).xy;
            vec2 coord = vUv - uDt * vel;
            vec4 advected = texture(uSource, coord);
            float pulseDissipation = clamp(uDissipation - (uKick * uKick) * 0.12, 0.82, 0.9995);
            outColor = advected * pulseDissipation;
        }
    """

    const val curlFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec2 texel = 1.0 / vec2(textureSize(uVelocity, 0));
            float vL = texture(uVelocity, vUv - vec2(texel.x, 0.0)).y;
            float vR = texture(uVelocity, vUv + vec2(texel.x, 0.0)).y;
            float vB = texture(uVelocity, vUv - vec2(0.0, texel.y)).x;
            float vT = texture(uVelocity, vUv + vec2(0.0, texel.y)).x;
            float curl = 0.5 * (vR - vL - vT + vB);
            outColor = vec4(curl, 0.0, 0.0, 1.0);
        }
    """

    const val vorticityFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uVelocity;
        uniform sampler2D uCurl;
        uniform float uDt;
        uniform float uConfinement;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec2 texel = 1.0 / vec2(textureSize(uVelocity, 0));
            float cL = abs(texture(uCurl, vUv - vec2(texel.x, 0.0)).x);
            float cR = abs(texture(uCurl, vUv + vec2(texel.x, 0.0)).x);
            float cB = abs(texture(uCurl, vUv - vec2(0.0, texel.y)).x);
            float cT = abs(texture(uCurl, vUv + vec2(0.0, texel.y)).x);
            float cC = texture(uCurl, vUv).x;

            vec2 grad = vec2(cR - cL, cT - cB);
            grad *= inversesqrt(max(dot(grad, grad), 1e-5));
            vec2 force = vec2(grad.y, -grad.x) * cC * uConfinement;

            vec2 vel = texture(uVelocity, vUv).xy;
            vel += force * uDt;
            outColor = vec4(vel, 0.0, 1.0);
        }
    """

    const val pressureFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;
        
        uniform sampler2D uPressure;
        uniform sampler2D uDivergence;
        
        in vec2 vUv;
        out vec4 outColor;
        
        void main() {
            vec2 texelSize = 1.0 / vec2(textureSize(uPressure, 0));
            float pL = texture(uPressure, vUv - vec2(texelSize.x, 0.0)).x;
            float pR = texture(uPressure, vUv + vec2(texelSize.x, 0.0)).x;
            float pB = texture(uPressure, vUv - vec2(0.0, texelSize.y)).x;
            float pT = texture(uPressure, vUv + vec2(0.0, texelSize.y)).x;
            float div = texture(uDivergence, vUv).x;
            
            // Jacobi iteration for pressure calculation
            outColor = vec4((pL + pR + pB + pT - div) * 0.25, 0.0, 0.0, 1.0);
        }
    """

    const val divergenceFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;
        
        uniform sampler2D uVelocity;
        
        in vec2 vUv;
        out vec4 outColor;
        
        void main() {
            vec2 texelSize = 1.0 / vec2(textureSize(uVelocity, 0));
            float vL = texture(uVelocity, vUv - vec2(texelSize.x, 0.0)).x;
            float vR = texture(uVelocity, vUv + vec2(texelSize.x, 0.0)).x;
            float vB = texture(uVelocity, vUv - vec2(0.0, texelSize.y)).y;
            float vT = texture(uVelocity, vUv + vec2(0.0, texelSize.y)).y;
            
            outColor = vec4(0.5 * (vR - vL + vT - vB), 0.0, 0.0, 1.0);
        }
    """

    const val gradientSubtractFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;
        
        uniform sampler2D uPressure;
        uniform sampler2D uVelocity;
        
        in vec2 vUv;
        out vec4 outColor;
        
        void main() {
            vec2 texelSize = 1.0 / vec2(textureSize(uPressure, 0));
            float pL = texture(uPressure, vUv - vec2(texelSize.x, 0.0)).x;
            float pR = texture(uPressure, vUv + vec2(texelSize.x, 0.0)).x;
            float pB = texture(uPressure, vUv - vec2(0.0, texelSize.y)).x;
            float pT = texture(uPressure, vUv + vec2(0.0, texelSize.y)).x;
            vec2 vel = texture(uVelocity, vUv).xy;
            
            // Subtract pressure gradient to ensure mass conservation (incompressibility)
            outColor = vec4(vel - vec2(pR - pL, pT - pB), 0.0, 1.0);
        }
    """

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

    const val displayFrag = """
    #version 300 es
    precision highp float;
    precision highp sampler2D;
    
    uniform sampler2D uDensity;
    uniform vec3 uBandsLow;
    uniform vec3 uBandsHigh;
    in vec2 vUv;
    out vec4 outColor;

    void main() {
        vec3 density = texture(uDensity, vUv).rgb;

        // 6-band neon palette map.
        vec3 c0 = vec3(0.95, 0.06, 0.30);
        vec3 c1 = vec3(0.70, 0.00, 1.00);
        vec3 c2 = vec3(0.24, 0.12, 1.00);
        vec3 c3 = vec3(0.00, 0.68, 1.00);
        vec3 c4 = vec3(0.18, 0.88, 1.00);
        vec3 c5 = vec3(1.00, 1.00, 1.00);

        vec3 weightsLo = max(uBandsLow, vec3(0.0));
        vec3 weightsHi = max(uBandsHigh, vec3(0.0));
        float sumW = dot(weightsLo, vec3(1.0)) + dot(weightsHi, vec3(1.0)) + 1e-4;

        vec3 spectral =
            c0 * weightsLo.x +
            c1 * weightsLo.y +
            c2 * weightsLo.z +
            c3 * weightsHi.x +
            c4 * weightsHi.y +
            c5 * weightsHi.z;
        spectral /= sumW;

        float luma = max(max(density.r, density.g), density.b);
        vec3 color = density * (1.2 + 3.0 * luma);
        color = mix(color, color * spectral, 0.62);
        color += spectral * luma * 0.45;

        outColor = vec4(color, clamp(luma * 2.6, 0.08, 1.0));
    }
"""

    const val bloomPrefilterFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uScene;
        uniform float uThreshold;

        in vec2 vUv;
        out vec4 outColor;

        void main() {
            vec3 c = texture(uScene, vUv).rgb;
            float peak = max(max(c.r, c.g), c.b);
            float w = max(peak - uThreshold, 0.0) / max(peak, 1e-4);
            outColor = vec4(c * w, 1.0);
        }
    """

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

    const val bloomCompositeFrag = """
        #version 300 es
        precision highp float;
        precision highp sampler2D;

        uniform sampler2D uScene;
        uniform sampler2D uBloom;
        uniform float uBloomStrength;

        in vec2 vUv;
        out vec4 outColor;

        vec3 ACESFilm(vec3 x) {
            float a = 2.51;
            float b = 0.03;
            float c = 2.43;
            float d = 0.59;
            float e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }

        void main() {
            vec3 scene = texture(uScene, vUv).rgb;
            vec3 bloom = texture(uBloom, vUv).rgb;
            vec3 color = scene + bloom * uBloomStrength;

            float dist = distance(vUv, vec2(0.5));
            float vignette = smoothstep(0.90, 0.35, dist);
            color *= mix(0.72, 1.0, vignette);

            color = ACESFilm(color);
            color = pow(color, vec3(1.0 / 2.2));

            float alpha = clamp(max(max(color.r, color.g), color.b) * 1.6, 0.15, 1.0);
            outColor = vec4(color, alpha);
        }
    """
}