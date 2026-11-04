package com.tigerplayer.ui.coverscreen.core

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient

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

@Composable
 fun rememberArtworkAccent(
    imageModel: Any?,
    fallback: Color = TigerNeonOrange
): Color {
    val context = LocalContext.current
    var accent by remember(imageModel) { mutableStateOf(fallback) }

    LaunchedEffect(imageModel) {
        val resolvedModel = imageModel ?: return@LaunchedEffect

        val request = ImageRequest.Builder(context)
            .data(resolvedModel)
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap

        accent = bitmap?.let { b ->
            val palette = Palette.from(b).generate()
            val colorInt = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: fallback.toArgb()

            Color(colorInt)
        } ?: fallback
    }

    return accent
}
@Composable
fun ArtworkBackdrop(
    imageModel: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = rememberArtworkAccent(
        imageModel = imageModel,
        fallback = TigerNeonOrange
    )

    val ambientBrush = rememberTigerAmbientGradient(
        accent = accent,
        baseTopAlpha = 0.22f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBrush)
    ) {
        WetPaintingFadeImage(
            imageModel = imageModel,
            contentDescription = contentDescription,
            fadeBottomPercent = 0.06f,
            blurRadius = 14f,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // optional readability overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
        )

        content()
    }
}
