package com.tigerplayer.ui.coverscreen.core

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun WetPaint(
    imageModel: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fadeBottomPercent: Float = 0.05f,
    blurRadius: Float = 14f,
    contentScale: ContentScale = ContentScale.Crop,
) {
    WetPaintingFadeImage(
        imageModel = imageModel,
        modifier = modifier,
        contentDescription = contentDescription,
        fadeBottomPercent = fadeBottomPercent,
        blurRadius = blurRadius,
        contentScale = contentScale
    )
}

@Composable
fun WetPaintingFadeImage(
    imageModel: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fadeBottomPercent: Float = 0.05f,
    blurRadius: Float = 14f,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val imageRequest = remember(imageModel) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .crossfade(true)
            .build()
    }

    val transitionStart = (1f - fadeBottomPercent).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        // LAYER 1: Soft Blurred Underlayer ("Wet Bleed")
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect
                            .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                    alpha = 0.85f
                }
        )

        // LAYER 2: Sharp Original fading out via DstIn alpha mask
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black,
                                transitionStart to Color.Black,
                                1.0f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}
