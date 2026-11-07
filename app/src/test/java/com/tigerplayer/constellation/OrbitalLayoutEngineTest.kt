package com.tigerplayer.constellation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrbitalLayoutEngineTest {

    private lateinit var layoutEngine: OrbitalLayoutEngine

    @Before
    fun setUp() {
        layoutEngine = OrbitalLayoutEngine()
    }

    @Test
    fun `layout maps graph nodes into positioned celestial nodes`() {
        val graph = createTestGraph()
        val positioned = layoutEngine.layout(graph)

        assertEquals(5, positioned.size)

        val core = positioned.find { it.type == NodeType.GALAXY_CORE }
        assertNotNull(core)
        assertEquals(0f, core!!.orbitRadius, 0.001f)

        val artist = positioned.find { it.id == "artist_radiohead" }
        assertNotNull(artist)
        assertTrue("Artist radius should be reeled in", artist!!.orbitRadius in 300f..3000f)

        val album = positioned.find { it.id == "album_ok_computer" }
        assertNotNull(album)
        assertEquals("artist_radiohead", album!!.orbitCenterId)
    }

    @Test
    fun `artist node radii are reeled in compared to legacy scale`() {
        val nodes = (1..25).map { i ->
            GraphNode(
                id = "artist_$i",
                label = "Artist $i",
                type = NodeType.ARTIST,
                importance = i / 25f
            )
        }
        val graph = ConstellationGraph(nodes = nodes, edges = emptyList(), seed = 42L)
        val positioned = layoutEngine.layout(graph)

        val maxRadius = positioned.filter { it.type == NodeType.ARTIST }.maxOf { it.orbitRadius }
        assertTrue("Max artist radius ($maxRadius) should be <= 3500f", maxRadius <= 3500f)
    }

    @Test
    fun `ConstellationPhysics computes live motion positions across time`() {
        val graph = createTestGraph()
        val positionedMap = layoutEngine.layout(graph).associateBy { it.id }

        val artist = positionedMap["artist_radiohead"]!!
        val posAtT0 = ConstellationPhysics.calculatePosition(artist, 0f, positionedMap)
        val posAtT100 = ConstellationPhysics.calculatePosition(artist, 100f, positionedMap)

        assertNotEquals("Artist position should move over time", posAtT0, posAtT100)
    }

    @Test
    fun `Child album node dynamically orbits moving artist parent`() {
        val graph = createTestGraph()
        val positionedMap = layoutEngine.layout(graph).associateBy { it.id }

        val artist = positionedMap["artist_radiohead"]!!
        val album = positionedMap["album_ok_computer"]!!

        val artistPosT0 = ConstellationPhysics.calculatePosition(artist, 0f, positionedMap)
        val albumPosT0 = ConstellationPhysics.calculatePosition(album, 0f, positionedMap)

        val distT0 = (albumPosT0 - artistPosT0).getDistance()
        assertTrue("Album should sit within its orbit radius from moving parent", distT0 <= album.orbitRadius * 1.1f + 10f)

        val artistPosT50 = ConstellationPhysics.calculatePosition(artist, 50f, positionedMap)
        val albumPosT50 = ConstellationPhysics.calculatePosition(album, 50f, positionedMap)

        assertNotEquals(albumPosT0, albumPosT50)
        val distT50 = (albumPosT50 - artistPosT50).getDistance()
        assertTrue("Album should track parent's moving frame of reference", distT50 <= album.orbitRadius * 1.1f + 10f)
    }

    private fun createTestGraph(): ConstellationGraph {
        val nodes = listOf(
            GraphNode(id = "core", label = "GALAXY CORE", type = NodeType.GALAXY_CORE, importance = 1f),
            GraphNode(id = "artist_radiohead", label = "Radiohead", type = NodeType.ARTIST, parentId = "core", importance = 0.9f),
            GraphNode(id = "artist_portishead", label = "Portishead", type = NodeType.ARTIST, parentId = "core", importance = 0.8f),
            GraphNode(id = "album_ok_computer", label = "OK Computer", type = NodeType.ALBUM, parentId = "artist_radiohead", importance = 0.7f),
            GraphNode(id = "track_paranoid", label = "Paranoid Android", type = NodeType.TRACK, parentId = "album_ok_computer", importance = 0.6f)
        )
        val edges = listOf(
            GraphEdge("core", "artist_radiohead", 0.9f),
            GraphEdge("artist_radiohead", "album_ok_computer", 0.8f),
            GraphEdge("album_ok_computer", "track_paranoid", 0.7f)
        )
        return ConstellationGraph(nodes = nodes, edges = edges, seed = 12345L)
    }
}
