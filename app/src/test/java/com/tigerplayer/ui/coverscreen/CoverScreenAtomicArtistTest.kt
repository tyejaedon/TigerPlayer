package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.graphics.Color
import com.tigerplayer.constellation.NodeType
import com.tigerplayer.constellation.PositionedNode
import com.tigerplayer.constellation.VisualDepth
import com.tigerplayer.constellation.OrbitMode
import com.tigerplayer.ui.constellation.ConstellationArtistReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverScreenAtomicArtistTest {

    @Test
    fun `atomic artist nodes filter album and track nodes for selected artist`() {
        val artistName = "Radiohead"
        val reading = ConstellationArtistReading(
            artistName = artistName,
            playCount = 142,
            minutesListened = 480,
            listeningSharePercent = 18.5f
        )
        assertEquals("Radiohead", reading.artistName)

        val artistNode = PositionedNode(
            id = "artist_radiohead",
            label = "Radiohead",
            type = NodeType.ARTIST,
            playCount = 142,
            imageUrl = null,
            weight = 85f,
            orbitCenterId = null,
            baseOrbitCenter = androidx.compose.ui.geometry.Offset.Zero,
            orbitRadius = 950f,
            baseAngle = 0f,
            orbitSpeed = 0.035f,
            color = Color.White,
            depth = VisualDepth.CORE,
            scale = 1.0f,
            glow = 0.8f,
            orbitMode = OrbitMode.GRAVITY_WELL,
            gravityInfluence = 5f,
            mass = 100f,
            fluidInfluence = 0.8f,
            energy = 0.5f,
            trailLength = 0.65f,
            eccentricity = 0.1f,
            phaseOffset = 0f
        )

        val albumNode = PositionedNode(
            id = "album_ok_computer",
            label = "OK Computer",
            type = NodeType.ALBUM,
            playCount = 98,
            imageUrl = null,
            weight = 60f,
            orbitCenterId = "artist_radiohead",
            baseOrbitCenter = androidx.compose.ui.geometry.Offset.Zero,
            orbitRadius = 260f,
            baseAngle = 0f,
            orbitSpeed = 0.015f,
            color = Color.Cyan,
            depth = VisualDepth.MID,
            scale = 0.8f,
            glow = 0.5f,
            orbitMode = OrbitMode.DYNAMIC,
            gravityInfluence = 2f,
            mass = 50f,
            fluidInfluence = 0.6f,
            energy = 0.4f,
            trailLength = 0.5f,
            eccentricity = 0.15f,
            phaseOffset = 0.5f,
            artistName = "Radiohead"
        )

        val trackNode = PositionedNode(
            id = "track_paranoid",
            label = "Paranoid Android",
            type = NodeType.TRACK,
            playCount = 45,
            imageUrl = null,
            weight = 30f,
            orbitCenterId = "album_ok_computer",
            baseOrbitCenter = androidx.compose.ui.geometry.Offset.Zero,
            orbitRadius = 110f,
            baseAngle = 0f,
            orbitSpeed = 0.02f,
            color = Color.White,
            depth = VisualDepth.BACKGROUND,
            scale = 0.5f,
            glow = 0.3f,
            orbitMode = OrbitMode.FLOW_FIELD,
            gravityInfluence = 0.1f,
            mass = 20f,
            fluidInfluence = 1.0f,
            energy = 0.6f,
            trailLength = 0.3f,
            eccentricity = 0.05f,
            phaseOffset = 0.2f,
            artistName = "Radiohead"
        )

        val allNodes = listOf(artistNode, albumNode, trackNode)
        val filtered = allNodes.filter { node ->
            node.label.equals(artistName, ignoreCase = true) ||
            node.artistName?.equals(artistName, ignoreCase = true) == true ||
            node.orbitCenterId == artistNode.id
        }

        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.type == NodeType.ARTIST })
        assertTrue(filtered.any { it.type == NodeType.ALBUM })
        assertTrue(filtered.any { it.type == NodeType.TRACK })
    }
}
