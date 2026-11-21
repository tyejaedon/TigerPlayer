package com.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure mathematical physics engine for the TigerPlayer Constellation galaxy map.
 * Computes deterministic spatial coordinates for celestial nodes based on continuous time,
 * orbital eccentricity, phase offsets, audio energy harmonics, and recursive parent-child motion.
 */
object ConstellationPhysics {

    /**
     * Calculates the exact spatial [Offset] of a celestial node at a given continuous [time] tick.
     * Recursively computes parent orbital centers so child systems (Albums/Tracks) dynamically orbit
     * their moving parents (Artists/Albums).
     */
    fun calculatePosition(
        node: PositionedNode,
        time: Float,
        allNodes: Map<String, PositionedNode>
    ): Offset {
        val center = if (node.orbitCenterId != null) {
            val parent = allNodes[node.orbitCenterId]
            if (parent != null) calculatePosition(parent, time, allNodes) else node.baseOrbitCenter
        } else {
            node.baseOrbitCenter
        }

        if (node.orbitRadius <= 0f) {
            return center
        }

        val currentAngle = node.baseAngle + node.phaseOffset + (time * node.orbitSpeed)
        val semiMajor = node.orbitRadius
        val semiMinor = semiMajor * sqrt((1f - node.eccentricity * node.eccentricity).coerceAtLeast(0.01f))

        // Harmonic audio energy pulse (micro-breathing motion)
        val pulseFactor = 1.0f + 0.03f * node.energy * sin(time * 0.05f + node.phaseOffset)

        // Raw unrotated ellipse coordinates
        val rawX = cos(currentAngle) * semiMajor * pulseFactor
        val rawY = sin(currentAngle) * semiMinor * pulseFactor

        // Rotate ellipse by baseAngle to orient orbital axis naturally in space
        val cosOrient = cos(node.baseAngle)
        val sinOrient = sin(node.baseAngle)

        val rotatedX = rawX * cosOrient - rawY * sinOrient
        val rotatedY = rawX * sinOrient + rawY * cosOrient

        return Offset(center.x + rotatedX, center.y + rotatedY)
    }
}
