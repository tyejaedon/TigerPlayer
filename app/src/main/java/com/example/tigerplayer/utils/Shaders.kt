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
        uniform float uKick;
        uniform float uDissipation; 
        uniform float uExpansion;
        uniform float uFlowSpeed;
        uniform float uTurbulence;
        uniform float uTime;
        
        in vec2 vUv;
        out vec4 outColor;

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

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
    
    // ACES Filmic Tone Mapping: Compresses HDR colors for high-end cinematic displays
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