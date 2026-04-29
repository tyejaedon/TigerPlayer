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
import java.util.Calendar

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
        "thunderstorm" -> Icons.Rounded.Thunderstorm
        "snow" -> Icons.Rounded.AcUnit
        "mist", "fog" -> Icons.Rounded.Dehaze
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
        // --- LAYER 1: ANIMATED SAMSUNG-STYLE SKY ---
        AnimatedWeatherBackground(
            isDay = weatherState.isDay,
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
// 🌌 THE ANIMATED SKY SYSTEM
// ==========================================================

@Composable
fun AnimatedWeatherBackground(
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "sky")

    // Slow drifting clouds mapped perfectly 0f to 1f for seamless wrapping
    val cloudFraction1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing), // LinearEasing guarantees no stutter
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud1"
    )

    val cloudFraction2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f, // Opposite direction
        animationSpec = infiniteRepeatable(
            animation = tween(70000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud2"
    )

    val skyTop = if (isDay) Color(0xFF64B5F6) else Color(0xFF071426)
    val skyBottom = if (isDay) Color(0xFFE3F2FD) else Color(0xFF0B1D3A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(skyTop, skyBottom)
                )
            )
    ) {

        // 🌤️ Cloud Layer 1 (far)
        CloudLayer(
            fraction = cloudFraction1,
            alpha = if (isDay) 0.35f else 0.15f,
            scale = 1.2f
        )

        // 🌥️ Cloud Layer 2 (mid)
        CloudLayer(
            fraction = cloudFraction2,
            alpha = if (isDay) 0.45f else 0.25f,
            scale = 1.6f
        )

        // Subtle atmospheric haze
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

@Composable
private fun CloudLayer(
    fraction: Float,
    alpha: Float,
    scale: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cloudColor = Color.White.copy(alpha = alpha)

        val width = size.width
        val shiftX = fraction * width

        // Double-draw method for seamless infinite wrapping
        translate(left = shiftX) {
            drawClouds(cloudColor, scale)
        }
        translate(left = shiftX - width) {
            drawClouds(cloudColor, scale)
        }
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