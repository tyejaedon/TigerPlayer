package com.tigerplayer.data.remote.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeezerModelsTest {

    private val gson = Gson()

    @Test
    fun `parses DeezerSearchResponse of DeezerTrackDto with nested artist and album`() {
        val json = """
            {
              "data": [
                {
                  "id": 3135556,
                  "title": "Harder, Better, Faster, Stronger",
                  "title_short": "Harder, Better, Faster, Stronger",
                  "link": "https://www.deezer.com/track/3135556",
                  "duration": 224,
                  "rank": 950000,
                  "explicit_lyrics": false,
                  "preview": "https://cdns-preview-d.dzcdn.net/stream/c-deda7fa9316d9e9e880d2c6207e92260-10.mp3",
                  "bpm": 123.4,
                  "gain": -11.2,
                  "artist": {
                    "id": 27,
                    "name": "Daft Punk",
                    "picture_xl": "https://e-cdns-images.dzcdn.net/images/artist/xl.jpg",
                    "picture_big": "https://e-cdns-images.dzcdn.net/images/artist/big.jpg"
                  },
                  "album": {
                    "id": 302127,
                    "title": "Discovery",
                    "cover_xl": "https://e-cdns-images.dzcdn.net/images/cover/xl.jpg",
                    "cover_big": "https://e-cdns-images.dzcdn.net/images/cover/big.jpg"
                  }
                }
              ],
              "total": 1
            }
        """.trimIndent()

        val type = object : TypeToken<DeezerSearchResponse<DeezerTrackDto>>() {}.type
        val parsed: DeezerSearchResponse<DeezerTrackDto> = gson.fromJson(json, type)

        assertEquals(1, parsed.data.size)
        val track = parsed.data.first()
        assertEquals(3135556L, track.id)
        assertEquals("Harder, Better, Faster, Stronger", track.title)
        assertEquals(224, track.duration)
        assertEquals(-11.2f, track.gain)
        assertEquals("https://cdns-preview-d.dzcdn.net/stream/c-deda7fa9316d9e9e880d2c6207e92260-10.mp3", track.preview)
        assertNotNull(track.artist)
        assertEquals("Daft Punk", track.artist?.name)
        assertEquals("https://e-cdns-images.dzcdn.net/images/artist/xl.jpg", track.artist?.bestPictureUrl)
        assertNotNull(track.album)
        assertEquals("Discovery", track.album?.title)
        assertEquals("https://e-cdns-images.dzcdn.net/images/cover/xl.jpg", track.album?.bestCoverUrl)
    }

    @Test
    fun `bestCoverUrl and bestPictureUrl fall back from xl to big to medium`() {
        val album = DeezerAlbumDto(
            id = 1L,
            title = "Album",
            cover = "https://cover",
            coverMedium = "https://cover_medium",
            coverBig = "https://cover_big",
            coverXl = null
        )
        assertEquals("https://cover_big", album.bestCoverUrl)

        val artist = DeezerArtistDto(
            id = 2L,
            name = "Artist",
            picture = "https://pic",
            pictureMedium = "https://pic_medium",
            pictureBig = null,
            pictureXl = ""
        )
        assertEquals("https://pic_medium", artist.bestPictureUrl)
    }
}
