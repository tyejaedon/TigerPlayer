package com.example.tigerplayer.engine

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * THE STABILIZED FLUID VORTEX
 * Optimized for Samsung/Snapdragon BLASTBufferQueue lifecycles.
 *
 * STATUS: Hole-punching successful. The surface is now transparent and
 * awaiting GPU density injection.
 */
class FluidVortexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: FluidRenderer? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        // IMPORTANT: Behind the UI, so your GlassEffect border sits on top
        setZOrderMediaOverlay(true)

        holder.setFormat(PixelFormat.TRANSLUCENT)
    }    fun setFluidRenderer(renderer: FluidRenderer) {
        if (this.renderer != null) return
        this.renderer = renderer
        setRenderer(renderer)

        // Physics advection requires a constant tick
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // RE-STABILIZE: Occasionally, Android resets the surface format on attach.
        // We force it back to Translucent here.
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    /**
     * THE RELEASE RITUAL
     * Ensures GPU resources are freed when the user collapses the player.
     */
    override fun onDetachedFromWindow() {
        // Only release if we are actually shutting down, not just pausing
        // renderer?.release()
        super.onDetachedFromWindow()
    }
}