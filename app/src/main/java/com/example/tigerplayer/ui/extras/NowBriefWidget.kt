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
            else -> "THE NIGHT IS DARK, BRUCE."
        }
    }

    // Dynamic contrast adaptation based on the sky background
    val contentColor = if (weatherState.isDay) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val secondaryContentColor = contentColor.copy(alpha = 0.7f)
    val ambientGlowColor = if (weatherState.isDay) Color(0xFFFF9100) else MaterialTheme.aardBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(16.dp, MaterialTheme.shapes.extraLarge, spotColor = ambientGlowColor.copy(alpha = 0.4f))
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClick {
                isExpanded = !isExpanded
                onWidgetClick()
            }
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    ) {
        // --- LAYER 1: THE DYNAMIC PARTICLE WEATHER SKY ---
        AnimatedWeatherBackground(
            isDay = weatherState.isDay,
            condition = weatherState.condition,
            modifier = Modifier.matchParentSize()
        )

        // --- LAYER 2: AMBIENT OVERLAY GLOW ---
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ambientGlowColor.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(1000f, 0f),
                        radius = 800f
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.extraLarge)
        )

        // --- LAYER 3: FOREGROUND DATA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weatherState.location.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryContentColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Location",
                    tint = secondaryContentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = greeting,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, color = contentColor)) {
                                append(weatherState.temperature)
                            }
                            withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ambientGlowColor, baselineShift = BaselineShift.Superscript)) {
                                append("°")
                            }
                        }
                    )
                    Text(
                        text = weatherState.condition.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = ambientGlowColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(ambientGlowColor.copy(alpha = 0.2f), CircleShape)
                            .blur(16.dp)
                    )
                    Icon(
                        imageVector = weatherState.weatherIcon,
                        contentDescription = weatherState.condition,
                        tint = if (weatherState.isDay) Color(0xFFE65100) else ambientGlowColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BriefStatChip(icon = Icons.Rounded.Air, value = weatherState.windSpeed, label = "WIND", weight = 1f, contentColor = contentColor)
                BriefStatChip(icon = Icons.Rounded.WaterDrop, value = weatherState.humidity, label = "HUMIDITY", weight = 1f, contentColor = contentColor)
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 20.dp),
                        color = contentColor.copy(alpha = 0.1f)
                    )

                    Text(
                        text = "ATMOSPHERIC INTEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = ambientGlowColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val intel = "Core temperature is holding at ${weatherState.temperature}° with ${weatherState.condition.lowercase()} in the immediate vicinity. Winds are tracking at ${weatherState.windSpeed}. Atmospheric conditions are nominal."

                    Text(
                        text = intel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ==========================================================
// 🌌 THE ADVANCED ANIMATED SKY SYSTEM
// ==========================================================

@Composable
fun AnimatedWeatherBackground(
    isDay: Boolean,
    condition: String,
    modifier: Modifier = Modifier
) {
    val cond = condition.lowercase()
    val isRain = cond.contains("rain") || cond.contains("drizzle")
    val isStorm = cond.contains("thunderstorm") || cond.contains("storm")
    val isSnow = cond.contains("snow")
    val isFog = cond.contains("mist") || cond.contains("fog") || cond.contains("haze")
    val isClear = cond.contains("clear")
    val isCloudy = !isClear

    val transition = rememberInfiniteTransition(label = "sky")

    // --- INFINITE ANIMATORS ---
    val cloudFraction1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing), RepeatMode.Restart), label = "cloud1"
    )
    val cloudFraction2 by transition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(70000, easing = LinearEasing), RepeatMode.Restart), label = "cloud2"
    )

    // Fast cycle for precipitation (rain/snow)
    val precipitationFraction by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isSnow) 3000 else 1000, easing = LinearEasing), RepeatMode.Restart), label = "precip"
    )

    // Twinkling effect for stars
    val twinklePhase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "twinkle"
    )

    // --- STORM LIGHTNING ENGINE ---
    var lightningAlpha by remember { mutableFloatStateOf(0f) }
    if (isStorm) {
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(Random.nextLong(2000, 7000))
                lightningAlpha = 0.8f
                delay(50)
                lightningAlpha = 0.2f
                delay(50)
                lightningAlpha = 0.9f
                delay(100)
                lightningAlpha = 0f
            }
        }
    }

    // --- THEME COLORS ---
    val skyTop = when {
        isStorm -> Color(0xFF1E272E)
        isDay -> Color(0xFF64B5F6)
        else -> Color(0xFF071426)
    }
    val skyBottom = when {
        isStorm -> Color(0xFF34495E)
        isFog -> Color(0xFF95A5A6)
        isDay -> Color(0xFFE3F2FD)
        else -> Color(0xFF0B1D3A)
    }

    val baseCloudAlpha = if (isStorm) 0.8f else if (isDay) 0.4f else 0.2f
    val cloudColor = if (isStorm) Color(0xFF2C3E50) else Color.White

    // --- PRECOMPUTED PARTICLES ---
    val rainDrops = remember { List(80) { Offset(Random.nextFloat(), Random.nextFloat()) to (Random.nextFloat() * 0.5f + 0.5f) } }
    val snowflakes = remember { List(60) { Offset(Random.nextFloat(), Random.nextFloat()) to (Random.nextFloat() * 0.5f + 0.2f) } }
    val stars = remember { List(50) { Offset(Random.nextFloat(), Random.nextFloat()) to (Random.nextFloat() * 2 * PI).toFloat() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(skyTop, skyBottom)))
    ) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. LIGHTNING FLASH
            if (isStorm && lightningAlpha > 0f) {
                drawRect(color = Color.White.copy(alpha = lightningAlpha))
            }

            // 2. STARS (Only visible at night with clear skies)
            if (!isDay && isClear) {
                stars.forEach { (pos, phase) ->
                    val alpha = 0.2f + 0.8f * sin(twinklePhase + phase).absoluteValue
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 2.5f,
                        center = Offset(pos.x * w, pos.y * h)
                    )
                }
            }

            // 3. CLOUDS
            if (isCloudy) {
                val layer1Alpha = baseCloudAlpha * 0.8f
                translate(left = cloudFraction1 * w) { drawClouds(cloudColor.copy(alpha = layer1Alpha), 1.2f) }
                translate(left = (cloudFraction1 * w) - w) { drawClouds(cloudColor.copy(alpha = layer1Alpha), 1.2f) }

                translate(left = cloudFraction2 * w) { drawClouds(cloudColor.copy(alpha = baseCloudAlpha), 1.6f) }
                translate(left = (cloudFraction2 * w) - w) { drawClouds(cloudColor.copy(alpha = baseCloudAlpha), 1.6f) }
            }

            // 4. RAIN OR THUNDERSTORM
            if (isRain || isStorm) {
                rainDrops.forEach { (pos, speedMultiplier) ->
                    val x = pos.x * w
                    // Modulo ensures they loop endlessly
                    val y = ((pos.y + precipitationFraction * speedMultiplier) % 1f) * h
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(x, y),
                        end = Offset(x + 10f, y + 40f), // Slanted trajectory
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 5. SNOW
            if (isSnow) {
                snowflakes.forEach { (pos, speedMultiplier) ->
                    // Adds horizontal sway to the snowflakes using a sine wave
                    val swayX = sin(precipitationFraction * PI * 4 + pos.y * 10).toFloat() * 25f
                    val x = (pos.x * w) + swayX
                    val y = ((pos.y + precipitationFraction * speedMultiplier) % 1f) * h
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = 4f + (speedMultiplier * 4f),
                        center = Offset(x, y)
                    )
                }
            }

            // 6. FOG / MIST LAYER
            if (isFog) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f)),
                        startY = h * 0.3f,
                        endY = h
                    )
                )
            }
        }

        // Subtle atmospheric foreground haze
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDay) 0.12f else 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

private fun DrawScope.drawClouds(color: Color, scale: Float) {
    drawCircle(
        color = color,
        radius = 120f * scale,
        center = center.copy(x = center.x - 200f)
    )

    drawCircle(
        color = color,
        radius = 160f * scale,
        center = center.copy(
            x = center.x + 50f,
            y = center.y - 120f
        )
    )

    drawCircle(
        color = color,
        radius = 140f * scale,
        center = center.copy(
            x = center.x + 250f,
            y = center.y + 100f
        )
    )
}

@Composable
private fun RowScope.BriefStatChip(icon: ImageVector, value: String, label: String, weight: Float, contentColor: Color) {
    Row(
        modifier = Modifier
            .weight(weight)
            .clip(MaterialTheme.shapes.medium)
            .background(contentColor.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = contentColor.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(text = value, style = MaterialTheme.typography.titleSmall, color = contentColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
        }
    }
}