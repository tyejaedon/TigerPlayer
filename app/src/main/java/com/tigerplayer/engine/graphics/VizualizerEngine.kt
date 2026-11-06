package com.tigerplayer.engine

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * A specialized [GLSurfaceView] configured for hole-punching transparency and BLASTBufferQueue lifecycles,
 * hosting the real-time audio-reactive fluid visualizer surface.
 *
 * Configures an OpenGL ES 3.0 context with translucent pixel format and Z-ordering overlay.
 */
class FluidVortexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: FluidRenderer? = null

    init {
        // Request OpenGL ES 3.0 context and 8-bit RGBA color buffer with 16-bit depth buffer
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        // Ensure surface sits behind UI elements while maintaining media overlay layer support
        setZOrderMediaOverlay(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    /**
     * Attaches and initializes the [FluidRenderer] for continuous physics advection rendering.
     *
     * @param renderer The [FluidRenderer] instance to render into this surface.
     */
    fun setFluidRenderer(renderer: FluidRenderer) {
        if (this.renderer != null) return
        this.renderer = renderer
        setRenderer(renderer)
        // Physics advection requires continuous 60fps rendering
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-enforce translucent surface format in case Android resets it on attach
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    override fun onDetachedFromWindow() {
        // Release GPU textures and FBO resources when the player view is detached
        renderer?.release()
        super.onDetachedFromWindow()
    }
}
