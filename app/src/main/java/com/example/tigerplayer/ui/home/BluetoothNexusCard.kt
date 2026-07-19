package com.example.tigerplayer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.utils.BluetoothDeviceInfo
import androidx.compose.ui.util.lerp

@Composable
fun BluetoothNexusCard(
    deviceInfo: BluetoothDeviceInfo,
    modifier: Modifier = Modifier
) {
    if (!deviceInfo.isConnected) return

    val codecLabel = deviceInfo.codec.ifBlank { "N/A" }
    val profileLabel = deviceInfo.profile.ifBlank { "A2DP" }
    val transportLabel = deviceInfo.transport.ifBlank { "Unknown" }
    val classLabel = deviceInfo.deviceClass.ifBlank { "Audio Device" }
    val addressLabel = deviceInfo.maskedAddress.ifBlank { "Hidden" }
    val batteryText = if (deviceInfo.batteryLevel >= 0) "${deviceInfo.batteryLevel}%" else "Unknown"
    val batteryColor = when {
        deviceInfo.batteryLevel < 0 -> Color.White.copy(alpha = 0.7f)
        deviceInfo.batteryLevel < 20 -> TigerNeonOrange
        else -> TigerCyberCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f), MaterialTheme.shapes.extraLarge)
            .bounceClick { isExpanded = !isExpanded }
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(TigerCyberCyan.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, TigerCyberCyan.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BluetoothEarbudsGlyph(
                    color = TigerCyberCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TigerCyberCyan.copy(alpha = pulseAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deviceInfo.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = if (isExpanded) "Bluetooth nexus online" else "$codecLabel • $profileLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = batteryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = batteryColor
                )
                Text(
                    text = "BATTERY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 8.sp
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "CODEC",
                        value = codecLabel,
                        valueColor = TigerCyberCyan
                    )
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "PROFILE",
                        value = profileLabel,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "TRANSPORT",
                        value = transportLabel,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "CLASS",
                        value = classLabel,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "TOTAL ASCENSION",
                        value = formatListeningTime(deviceInfo.listeningTimeMs),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    NexusMetricTile(
                        modifier = Modifier.weight(1f),
                        title = "ADDRESS",
                        value = addressLabel,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        BluetoothStatusChip(label = if (isExpanded) "NEXUS CONNECTED" else "CONNECTED · TAP TO EXPAND")
    }
}

@Composable
private fun NexusMetricTile(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BluetoothStatusChip(label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(TigerCyberCyan.copy(alpha = 0.12f))
            .border(1.dp, TigerCyberCyan.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(TigerCyberCyan, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TigerCyberCyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun formatListeningTime(listeningTimeMs: Long): String {
    val totalSecs = listeningTimeMs / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    return "${hours}H ${minutes}M"
}

@Composable
private fun BluetoothEarbudsGlyph(
    color: Color,
    modifier: Modifier = Modifier
) {
    AnimatedEarbudsDock3D(
        accentColor = color,
        modifier = modifier
    )
}

@Suppress("unused")
@Composable
fun FloatingEarbudsVector(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    maxLift: Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "earbudsVectorFloat")
    val floatProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vectorFloatProgress"
    )
    val lightSweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vectorLightSweep"
    )

    val yOffset = lerp(0f, -maxLift.value, floatProgress)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val shadowWidth = lerp(size.width * 0.42f, size.width * 0.58f, floatProgress)
            val shadowHeight = lerp(size.height * 0.14f, size.height * 0.2f, floatProgress)
            val shadowTop = lerp(size.height * 0.7f, size.height * 0.78f, floatProgress)
            drawOval(
                color = Color.Black.copy(alpha = lerp(0.28f, 0.16f, floatProgress)),
                topLeft = Offset((size.width - shadowWidth) / 2f, shadowTop),
                size = Size(shadowWidth, shadowHeight)
            )
        }

        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Floating 3D earbuds",
            tint = Color.Unspecified,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = yOffset.dp)
                .graphicsLayer {
                    scaleX = lerp(1f, 1.03f, floatProgress)
                    scaleY = lerp(1f, 1.03f, floatProgress)
                }
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val sweepX = lerp(-size.width * 0.4f, size.width * 1.3f, lightSweep)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    start = Offset(sweepX, 0f),
                    end = Offset(sweepX + size.width * 0.28f, size.height)
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
fun AnimatedEarbudsDock3D(
    modifier: Modifier = Modifier,
    accentColor: Color = TigerCyberCyan
) {
    val motion = rememberEarbuds3DMotion()

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = motion.microTiltDegrees
        }
    ) {
        val w = size.width
        val h = size.height

        val bodyWidth = w * 0.78f
        val bodyHeight = h * 0.36f
        val bodyLeft = (w - bodyWidth) / 2f
        val bodyTop = h * 0.42f

        val lift = lerp(0f, h * 0.14f, motion.floatProgress)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3E454F), Color(0xFF111317))
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(bodyHeight * 0.32f, bodyHeight * 0.32f)
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1B1F25), Color(0xFF050608))
            ),
            topLeft = Offset(bodyLeft + bodyWidth * 0.05f, bodyTop + bodyHeight * 0.08f),
            size = Size(bodyWidth * 0.9f, bodyHeight * 0.48f),
            cornerRadius = CornerRadius(bodyHeight * 0.16f, bodyHeight * 0.16f)
        )

        val earRadius = w * 0.11f
        val leftCenter = Offset(w * 0.37f, h * 0.47f - lift)
        val rightCenter = Offset(w * 0.63f, h * 0.47f - lift)

        drawEarbud(
            center = leftCenter,
            radius = earRadius,
            liftProgress = motion.floatProgress,
            ledColor = Color(0xFF00E5FF),
            ledPulse = motion.ledPulse,
            stemLean = -earRadius * 0.08f
        )
        drawEarbud(
            center = rightCenter,
            radius = earRadius,
            liftProgress = motion.floatProgress,
            ledColor = Color(0xFFFF7FEF),
            ledPulse = motion.ledPulse,
            stemLean = earRadius * 0.08f
        )

        val lidWidth = bodyWidth * 0.96f
        val lidHeight = bodyHeight * 0.24f
        val lidLeft = (w - lidWidth) / 2f
        val lidTop = bodyTop - lidHeight * 0.82f
        val lidPivot = Offset(w / 2f, bodyTop + bodyHeight * 0.04f)

        withTransform({
            rotate(
                degrees = lerp(0f, -33f, motion.lidOpenProgress),
                pivot = lidPivot
            )
        }) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5A6170), Color(0xFF191C22))
                ),
                topLeft = Offset(lidLeft, lidTop),
                size = Size(lidWidth, lidHeight),
                cornerRadius = CornerRadius(lidHeight * 0.65f, lidHeight * 0.65f)
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(lidLeft + lidWidth * 0.08f, lidTop + lidHeight * 0.2f),
                size = Size(lidWidth * 0.84f, lidHeight * 0.26f),
                cornerRadius = CornerRadius(lidHeight * 0.22f, lidHeight * 0.22f)
            )
        }

        val caseLedCenter = Offset(w * 0.5f, bodyTop + bodyHeight * 0.82f)
        drawCircle(
            color = accentColor.copy(alpha = lerp(0.28f, 0.95f, motion.ledPulse)),
            center = caseLedCenter,
            radius = w * 0.03f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            center = caseLedCenter,
            radius = w * 0.013f
        )

        val sweepX = lerp(-w * 0.35f, w * 1.25f, motion.lightSweep)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                start = Offset(sweepX, 0f),
                end = Offset(sweepX + w * 0.25f, h)
            ),
            blendMode = BlendMode.Screen
        )
    }
}

private data class Earbuds3DMotion(
    val floatProgress: Float,
    val lidOpenProgress: Float,
    val ledPulse: Float,
    val lightSweep: Float,
    val microTiltDegrees: Float
)

@Composable
private fun rememberEarbuds3DMotion(): Earbuds3DMotion {
    val transition = rememberInfiniteTransition(label = "earbuds3dMotion")

    val floatProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatProgress"
    )
    val lidOpenProgress by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lidOpenProgress"
    )
    val ledPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ledPulse"
    )
    val lightSweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lightSweep"
    )
    val microTiltDegrees by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "microTilt"
    )

    return Earbuds3DMotion(
        floatProgress = floatProgress,
        lidOpenProgress = lidOpenProgress,
        ledPulse = ledPulse,
        lightSweep = lightSweep,
        microTiltDegrees = microTiltDegrees
    )
}

private fun DrawScope.drawEarbud(
    center: Offset,
    radius: Float,
    liftProgress: Float,
    ledColor: Color,
    ledPulse: Float,
    stemLean: Float
) {
    val stemWidth = radius * 0.42f
    val stemHeight = radius * 1.52f
    val stemTop = center.y + radius * 0.45f
    val stemLeft = center.x - stemWidth / 2f + stemLean


    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.95f), Color(0xFFB0B5C5)),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
            radius = radius * 1.35f
        ),
        center = center,
        radius = radius
    )

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFF2F4FF), Color(0xFFAAB0C4))
        ),
        topLeft = Offset(stemLeft, stemTop),
        size = Size(stemWidth, stemHeight),
        cornerRadius = CornerRadius(stemWidth / 2f, stemWidth / 2f)
    )

    drawCircle(
        color = ledColor.copy(alpha = lerp(0.35f, 0.96f, ledPulse)),
        center = Offset(center.x + stemLean * 1.8f, center.y + radius * 0.44f),
        radius = radius * 0.2f
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        center = Offset(center.x - radius * 0.32f, center.y - radius * 0.38f),
        radius = radius * 0.16f
    )
}
