package com.example.tigerplayer.ui.extras

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.tigerplayer.ui.theme.glassEffect
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

    // THE FIX: Properly routing the new ViewModel state variables into the UI
    val weatherState = when (uiState) {
        is WeatherUiState.Loading -> WeatherState(
            temperature = "--", condition = "SCANNING SKY...", location = "NAIROBI",
            windSpeed = "--", humidity = "--", isDay = fallbackIsDay,
            weatherIcon = Icons.Rounded.Sync
        )
        is WeatherUiState.Success -> WeatherState(
            temperature = uiState.temperature, condition = uiState.condition, location = "NAIROBI",
            windSpeed = uiState.windSpeed, // Wired!
            humidity = uiState.humidity, // THE FINAL WIRE IS CONNECTED
            isDay = uiState.isDay,         // Wired!
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
    // --- 1. THE EXPANSION STATE ---
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

    val ambientGlowColor = if (weatherState.isDay) Color(0xFFFF9100) else MaterialTheme.aardBlue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .bounceClick {
                // Instantly toggles the layout expansion on tap
                isExpanded = !isExpanded
                onWidgetClick()
            }
            .shadow(16.dp, MaterialTheme.shapes.extraLarge, spotColor = ambientGlowColor.copy(alpha = 0.2f))
            .glassEffect(MaterialTheme.shapes.extraLarge)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            // THE FIX: This single line forces the Box to fluidly animate its height when content appears/disappears
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ambientGlowColor.copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1000f, 0f),
                        radius = 800f
                    )
                )
        )

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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = greeting,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                            withStyle(SpanStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)) {
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
                        tint = ambientGlowColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BriefStatChip(icon = Icons.Rounded.Air, value = weatherState.windSpeed, label = "WIND", weight = 1f)
                BriefStatChip(icon = Icons.Rounded.WaterDrop, value = weatherState.humidity, label = "HUMIDITY", weight = 1f)
            }

            // --- 2. THE TACTICAL EXPANSION ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    Text(
                        text = "ATMOSPHERIC INTEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = ambientGlowColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // A dynamically generated intelligence readout
                    val intel = "Core temperature is holding at ${weatherState.temperature}° with ${weatherState.condition.lowercase()} in the immediate vicinity. Winds are tracking at ${weatherState.windSpeed}. Atmospheric conditions are nominal."

                    Text(
                        text = intel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BriefStatChip(icon: ImageVector, value: String, label: String, weight: Float) {
    Row(
        modifier = Modifier
            .weight(weight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(text = value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
        }
    }
}