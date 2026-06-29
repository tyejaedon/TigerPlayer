package com.example.tigerplayer.ui.extras

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.home.WeatherUiState
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.random.Random

data class WeatherState(
    val temperature: String,
    val condition: String,
    val location: String,
    val windSpeed: String,
    val humidity: String,
    val isDay: Boolean,
    val weatherIcon: ImageVector
)

private fun getWeatherIcon(condition: String, isDay: Boolean): ImageVector {
    return when (condition.lowercase()) {
        "clear sky" -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
        "few clouds", "scattered clouds", "broken clouds" -> Icons.Rounded.Cloud
        "shower rain", "rain", "drizzle", "light rain" -> Icons.Rounded.WaterDrop
        "thunderstorm", "storm" -> Icons.Rounded.Thunderstorm
        "snow", "light snow", "heavy snow" -> Icons.Rounded.AcUnit
        "mist", "fog", "haze" -> Icons.Rounded.Dehaze
        else -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    }
}

@Composable
fun NowBriefWidgetWrapper(
    uiState: WeatherUiState,
    onWidgetClick: () -> Unit = {}
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val fallbackIsDay = currentHour in 6..18

    val weatherState = when (uiState) {
        is WeatherUiState.Loading -> WeatherState(
            temperature = "--", condition = "SCANNING SKY...", location = "NAIROBI",
            windSpeed = "--", humidity = "--", isDay = fallbackIsDay,
            weatherIcon = Icons.Rounded.Sync
        )
        is WeatherUiState.Success -> WeatherState(
            temperature = uiState.temperature, condition = uiState.condition, location = "NAIROBI",
            windSpeed = uiState.windSpeed, humidity = uiState.humidity, isDay = uiState.isDay,
            weatherIcon = getWeatherIcon(uiState.condition, uiState.isDay)
        )
        is WeatherUiState.Error -> WeatherState(
            temperature = uiState.fallbackTemperature, condition = uiState.fallbackCondition, location = "NAIROBI",
            windSpeed = uiState.fallbackWindSpeed, humidity = "Offline", isDay = uiState.fallbackIsDay,
            weatherIcon = Icons.Rounded.CloudOff
        )
    }

    NowBriefWidget(weatherState = weatherState, onWidgetClick = onWidgetClick)
}

@Composable
fun NowBriefWidget(
    weatherState: WeatherState,
    onWidgetClick: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    val greeting = remember(weatherState.isDay) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "MORNING, Witcher."
            in 12..16 -> "GOOD AFTERNOON, Padawan."
            in 17..20 -> "EVENING, Slayer."
            else -> "GOOD NIGHT, Hunter."
        }
    }

    val contentColor = if (weatherState.isDay) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val secondaryContentColor = contentColor.copy(alpha = 0.65f)
    val ambientGlowColor = if (weatherState.isDay) Color(0xFFFF9100) else Color(0xFF4FC3F7)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(12.dp, MaterialTheme.shapes.extraLarge, spotColor = ambientGlowColor.copy(alpha = 0.3f))
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClick {
                isExpanded = !isExpanded
                onWidgetClick()
            }
            .animateContentSize()
    ) {
        // --- OPTIMIZED WEATHER BACKDROP ---
        AnimatedWeatherBackground(
            isDay = weatherState.isDay,
            condition = weatherState.condition,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = weatherState.weatherIcon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 52.sp, fontWeight = FontWeight.Black, color = contentColor)) {
                                append(weatherState.temperature)
                            }
                            withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ambientGlowColor, baselineShift = BaselineShift.Superscript)) {
                                append("°")
                            }
                        }
                    )
                    Text(
                        text = weatherState.condition.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryContentColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    BriefStatRow(icon = Icons.Rounded.Air, value = weatherState.windSpeed, contentColor = contentColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    BriefStatRow(icon = Icons.Rounded.WaterDrop, value = weatherState.humidity, contentColor = contentColor)
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = contentColor.copy(alpha = 0.1f))
                    Text(
                        text = "Current atmospheric conditions in ${weatherState.location} are nominal. Perfect for a deep listening session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefStatRow(icon: ImageVector, value: String, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = contentColor.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AnimatedWeatherBackground(
    isDay: Boolean,
    condition: String,
    modifier: Modifier = Modifier
) {
    val cond = condition.lowercase()
    val isRain = cond.contains("rain") || cond.contains("drizzle")
    val isSnow = cond.contains("snow")
    val isStorm = cond.contains("storm") || cond.contains("thunderstorm")
    
    val transition = rememberInfiniteTransition(label = "weather")
    val cycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "cycle"
    )

    // Precomputed particle offsets for performance
    val particles = remember(isRain, isSnow) {
        val count = if (isRain) 30 else if (isSnow) 20 else 0
        List(count) { Offset(Random.nextFloat(), Random.nextFloat()) }
    }

    val skyTop = if (isDay) Color(0xFF74B9FF) else Color(0xFF0F2027)
    val skyBottom = if (isDay) Color(0xFFA2D2FF) else Color(0xFF2C5364)

    Box(modifier.background(Brush.verticalGradient(listOf(skyTop, skyBottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            if (isRain || isStorm) {
                particles.forEach { p ->
                    val x = p.x * w
                    val y = ((p.y + cycle) % 1f) * h
                    drawLine(Color.White.copy(0.4f), Offset(x, y), Offset(x + 5f, y + 25f), strokeWidth = 2f)
                }
            } else if (isSnow) {
                particles.forEach { p ->
                    val x = p.x * w + sin(cycle * PI * 2 + p.y * 5).toFloat() * 10f
                    val y = ((p.y + cycle * 0.5f) % 1f) * h
                    drawCircle(Color.White.copy(0.6f), radius = 3f, center = Offset(x, y))
                }
            }
        }
    }
}
