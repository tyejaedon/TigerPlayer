package com.tigerplayer.engine

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Translucent GLSurfaceView host for OpenGL fluid visualization.
 * Configured with 32-bit RGBA8888 translucent pixel format and Z-order media overlay
 * to composite behind Compose UI glassmorphic layers without surface flashing.
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
     * Releases GPU textures, shaders, and FBO attachments when detached from window.
     */
    override fun onDetachedFromWindow() {
        // FIXED: Restore the release call to prevent orphan GPU textures/FBOs
        renderer?.release()
        super.onDetachedFromWindow()
    }
}