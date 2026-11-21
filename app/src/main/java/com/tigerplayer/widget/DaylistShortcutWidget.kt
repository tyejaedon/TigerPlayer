package com.tigerplayer.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import com.tigerplayer.data.repository.AudioRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Optional Glance shortcut widget surfacing the user's current time-of-day Day List
 * segment and top Heavy Rotation track from [AudioRepository].
 */
class DaylistShortcutWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DaylistEntryPoint {
        fun audioRepository(): AudioRepository
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 56.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DaylistEntryPoint::class.java
        )
        val repository = entryPoint.audioRepository()
        val segment = currentDaySegment()
        val since14Days = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
        val since30Days = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

        val daylistCount = runCatching {
            repository.getNeonDaylistTracks(segment, since14Days, 20).firstOrNull()?.size ?: 0
        }.getOrDefault(0)

        val heavyRotationTop = runCatching {
            repository.getHeavyRotation(since30Days).firstOrNull()?.firstOrNull()
        }.getOrNull()

        val subtitle = when {
            heavyRotationTop != null -> "${heavyRotationTop.title} • ${heavyRotationTop.playCount} plays"
            daylistCount > 0 -> "$daylistCount tracks ready"
            else -> context.getString(R.string.widget_heavy_rotation_subtitle)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                DaylistShortcutContent(
                    segmentLabel = segment.lowercase().replaceFirstChar { it.uppercase() },
                    subtitle = subtitle,
                    launchIntent = launchIntent
                )
            }
        }
    }

    companion object {
        fun currentDaySegment(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
            return when (hourOfDay) {
                in 5..11 -> "MORNING"
                in 12..16 -> "AFTERNOON"
                in 17..21 -> "EVENING"
                else -> "NIGHT"
            }
        }
    }
}

@Composable
private fun DaylistShortcutContent(
    segmentLabel: String,
    subtitle: String,
    launchIntent: Intent
) {
    val context = LocalContext.current
    val accent = Color(WidgetPlaybackState.DEFAULT_SECONDARY_NEON_ARGB)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(launchIntent))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_daylist),
            contentDescription = context.getString(R.string.widget_daylist_title),
            colorFilter = ColorFilter.tint(ColorProvider(accent)),
            modifier = GlanceModifier.size(28.dp)
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$segmentLabel ${context.getString(R.string.widget_daylist_title)}",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = subtitle,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}

class DaylistShortcutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaylistShortcutWidget()
}
