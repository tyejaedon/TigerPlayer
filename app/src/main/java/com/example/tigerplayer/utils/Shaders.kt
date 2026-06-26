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
        
        in vec2 vUv;
        out vec4 outColor;
        
        void main() {
            // Back-trace the velocity to find the source of the fluid 'ink'
            vec2 vel = texture(uVelocity, vUv).xy;
            vec2 coord = vUv - uDt * vel;
            outColor = texture(uSource, coord) * uDissipation;
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
    in vec2 vUv;
    out vec4 outColor;
    
    // ACES Filmic Tone Mapping: Compresses HDR colors for high-end cinematic displays
    vec3 ACESFilm(vec3 x) {
        float a = 2.51; float b = 0.03; float c = 2.43; float d = 0.59; float e = 0.14;
        return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
    }

    void main() {
        // 1. Raw Sample
        vec3 rawColor = texture(uDensity, vUv).rgb;
        float luma = length(rawColor);

        // 2. SUPREME AMPLIFICATION
        // On mobile, FFT data can be quiet. We use a non-linear gain 
        // to make subtle ripples look like glowing ink.
        float gain = 1.0 + 8.0 * smoothstep(0.0, 0.5, luma);
        vec3 color = rawColor * gain;

        // 3. WITCHER AESTHETIC: Chromatic Depth
        // We boost the "Aard Blue" or "Igni Red" components if they are dominant
        color.r *= 1.1; // Slight warmth boost for Igni
        color.b *= 1.2; // Deep cold boost for Aard

        // 4. TONEMAPPING & GAMMA CORRECTION
        color = ACESFilm(color);
        color = pow(color, vec3(1.0 / 2.2)); // Corrects for Android's display gamma
        
        // 5. CINEMATIC VIGNETTE
        // Darkens the edges slightly to draw the eye into the vortex center
        float dist = distance(vUv, vec2(0.5));
        float vignette = smoothstep(0.85, 0.4, dist);
        color *= mix(0.7, 1.0, vignette);
        
        // 6. STABILIZED ALPHA (The "Hole-Punch" Fix)
        // If luma is low, we keep a faint 0.1 alpha to maintain the "Glass" look.
        // If luma is high, we push alpha to 1.0 for a solid, vibrant fluid effect.
        float alpha = clamp(luma * 4.0, 0.15, 1.0); 
        
        // Final Output using Straight Alpha for standard GLES 3.0 blending
        outColor = vec4(color, alpha);
    }
"""
}