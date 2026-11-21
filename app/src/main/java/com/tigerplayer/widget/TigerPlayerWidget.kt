package com.tigerplayer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tigerplayer.MainActivity
import com.tigerplayer.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

enum class WidgetLayoutVariant {
    COMPACT_2X2,
    SLIM_4X1,
    STANDARD_4X2
}

/**
 * Responsive Glance widget supporting:
 * - 2x2 compact (home screen & foldable cover screens: artwork + play/pause)
 * - 4x1 slim (title + artist + transport)
 * - 4x2 standard (artwork + title + artist + transport + progress)
 */
class TigerPlayerWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun widgetStateStore(): WidgetStateStore
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT_2X2, SIZE_SLIM_4X1, SIZE_STANDARD_4X2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val store = entryPoint.widgetStateStore()
        val initialState = store.read()

        provideContent {
            val state by store.stateFlow.collectAsState(initial = initialState)
            GlanceTheme {
                val variant = resolveLayoutVariant(LocalSize.current)
                when (variant) {
                    WidgetLayoutVariant.COMPACT_2X2 -> Compact2x2Content(state)
                    WidgetLayoutVariant.SLIM_4X1 -> Slim4x1Content(state)
                    WidgetLayoutVariant.STANDARD_4X2 -> Standard4x2Content(state)
                }
            }
        }
    }

    companion object {
        val SIZE_COMPACT_2X2 = DpSize(110.dp, 110.dp)
        val SIZE_SLIM_4X1 = DpSize(250.dp, 56.dp)
        val SIZE_STANDARD_4X2 = DpSize(250.dp, 110.dp)

        fun resolveLayoutVariant(size: DpSize): WidgetLayoutVariant {
            return when {
                size.width < 200.dp -> WidgetLayoutVariant.COMPACT_2X2
                size.height < 90.dp -> WidgetLayoutVariant.SLIM_4X1
                else -> WidgetLayoutVariant.STANDARD_4X2
            }
        }
    }
}

@Composable
private fun Compact2x2Content(state: WidgetPlaybackState) {
    val context = LocalContext.current
    val neonPrimary = Color(state.primaryNeonArgb)
    val artworkBitmap = loadCachedArtwork(state.artworkPath)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(8.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(14.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artworkBitmap != null) {
                Image(
                    provider = ImageProvider(artworkBitmap),
                    contentDescription = state.title.ifBlank { context.getString(R.string.widget_player_label) },
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp)
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music_note),
                    contentDescription = context.getString(R.string.widget_player_label),
                    colorFilter = ColorFilter.tint(ColorProvider(neonPrimary)),
                    modifier = GlanceModifier.size(36.dp)
                )
            }
        }

        // Neon accent indicator bar at top-left
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = GlanceModifier
                    .width(24.dp)
                    .height(4.dp)
                    .cornerRadius(2.dp)
                    .background(ColorProvider(neonPrimary))
            ) {}
        }

        Box(
            modifier = GlanceModifier.padding(4.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            PlayPauseButton(
                isPlaying = state.isPlaying,
                neonPrimary = neonPrimary,
                buttonSize = 40.dp
            )
        }
    }
}

@Composable
private fun Slim4x1Content(state: WidgetPlaybackState) {
    val context = LocalContext.current
    val neonPrimary = Color(state.primaryNeonArgb)
    val title = state.title.ifBlank { context.getString(R.string.widget_idle_title) }
    val artist = state.artist.ifBlank { context.getString(R.string.widget_idle_artist) }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(neonPrimary))
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = artist,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        TransportButtonsRow(
            isPlaying = state.isPlaying,
            neonPrimary = neonPrimary,
            compact = true
        )
    }
}

@Composable
private fun Standard4x2Content(state: WidgetPlaybackState) {
    val context = LocalContext.current
    val neonPrimary = Color(state.primaryNeonArgb)
    val artworkBitmap = loadCachedArtwork(state.artworkPath)
    val title = state.title.ifBlank { context.getString(R.string.widget_idle_title) }
    val artist = state.artist.ifBlank { context.getString(R.string.widget_idle_artist) }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(86.dp)
                .cornerRadius(14.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            if (artworkBitmap != null) {
                Image(
                    provider = ImageProvider(artworkBitmap),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp)
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music_note),
                    contentDescription = title,
                    colorFilter = ColorFilter.tint(ColorProvider(neonPrimary)),
                    modifier = GlanceModifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = artist,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            LinearProgressIndicator(
                progress = state.progressFraction,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .cornerRadius(2.dp),
                color = ColorProvider(neonPrimary),
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            TransportButtonsRow(
                isPlaying = state.isPlaying,
                neonPrimary = neonPrimary,
                compact = false
            )
        }
    }
}

@Composable
private fun TransportButtonsRow(
    isPlaying: Boolean,
    neonPrimary: Color,
    compact: Boolean
) {
    val context = LocalContext.current
    val secondarySize = if (compact) 34.dp else 36.dp
    val playSize = if (compact) 38.dp else 42.dp

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_skip_previous),
            contentDescription = context.getString(R.string.coverscreen_action_previous),
            onClick = actionRunCallback<SkipPreviousAction>(),
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            contentColor = GlanceTheme.colors.onSurface,
            modifier = GlanceModifier.size(secondarySize)
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        PlayPauseButton(
            isPlaying = isPlaying,
            neonPrimary = neonPrimary,
            buttonSize = playSize
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_skip_next),
            contentDescription = context.getString(R.string.coverscreen_action_next),
            onClick = actionRunCallback<SkipNextAction>(),
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            contentColor = GlanceTheme.colors.onSurface,
            modifier = GlanceModifier.size(secondarySize)
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    neonPrimary: Color,
    buttonSize: Dp
) {
    val context = LocalContext.current
    val iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    val descriptionRes = if (isPlaying) R.string.coverscreen_action_pause else R.string.coverscreen_action_play

    CircleIconButton(
        imageProvider = ImageProvider(iconRes),
        contentDescription = context.getString(descriptionRes),
        onClick = actionRunCallback<TogglePlayPauseAction>(),
        backgroundColor = ColorProvider(neonPrimary),
        contentColor = ColorProvider(Color.White),
        modifier = GlanceModifier.size(buttonSize)
    )
}

private fun loadCachedArtwork(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
}
