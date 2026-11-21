package com.tigerplayer.data.source

import android.content.Context
import android.net.Uri
import com.tigerplayer.data.model.ArtworkEditAction
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.BatchTagEditRequest
import com.tigerplayer.data.model.TagWriteResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class LocalTagDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataSource: LocalTagDataSource

    // Minimal valid 1x1 RGB PNG (67 bytes) so jaudiotagger's VorbisCommentTag can read image dimensions.
    private val sampleArtwork = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
        0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00,
        0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(), 0xB0.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        dataSource = LocalTagDataSource(context)
    }

    @Test
    fun `mp3 read write all fields and artwork round-trip`() {
        val mp3File = createMinimalMp3(tempFolder.newFile("sample.mp3"))
        assertRoundTripAllFieldsAndArtwork(mp3File)
    }

    @Test
    fun `flac read write all fields and artwork round-trip`() {
        val flacFile = copyResourceToTemp("sample.flac")
        assertRoundTripAllFieldsAndArtwork(flacFile)
    }

    @Test
    fun `ogg read write all fields and artwork round-trip`() {
        val oggFile = copyResourceToTemp("sample.ogg")
        assertRoundTripAllFieldsAndArtwork(oggFile)
    }

    @Test
    fun `m4a read write all fields and artwork round-trip`() {
        val m4aFile = copyResourceToTemp("sample.m4a")
        assertRoundTripAllFieldsAndArtwork(m4aFile)
    }

    private fun copyResourceToTemp(name: String): File {
        val file = tempFolder.newFile(name)
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    @Test
    fun `unsupported format and remote tracks fail explicitly`() = runTest {
        val remoteTrack = AudioTrack(
            id = "remote_1",
            title = "Remote Song",
            artist = "Artist",
            album = "Album",
            uri = Uri.EMPTY,
            artworkUri = Uri.EMPTY,
            durationMs = 10_000L,
            mimeType = "audio/mpeg",
            isLocal = false,
            isRemote = true
        )
        val result = dataSource.writeTags(
            tracks = listOf(remoteTrack),
            edit = BatchTagEditRequest(title = "New"),
            createBackup = false
        )
        assertTrue(result is TagWriteResult.Failure)
        val failure = result as TagWriteResult.Failure
        assertTrue(failure.errors.first().reason.isNotBlank())

        val wavTrack = remoteTrack.copy(
            id = "wav_1",
            title = "Wav Song",
            mimeType = "audio/wav",
            isLocal = true,
            isRemote = false,
            path = "/storage/emulated/0/Music/test.wav"
        )
        val wavResult = dataSource.writeTags(
            tracks = listOf(wavTrack),
            edit = BatchTagEditRequest(title = "New"),
            createBackup = false
        )
        assertTrue(wavResult is TagWriteResult.Failure)
        assertTrue((wavResult as TagWriteResult.Failure).errors.first().reason.contains("Unsupported"))
    }

    private fun assertRoundTripAllFieldsAndArtwork(file: File) {
        val written = dataSource.writeTagsToFile(
            file,
            BatchTagEditRequest(
                title = "Silver for Monsters",
                artist = "Marcin Przybylowicz",
                album = "The Witcher 3 OST",
                albumArtist = "CD Projekt Red",
                trackNumber = "4",
                discNumber = "2",
                year = "2015",
                genre = "Soundtrack",
                artworkAction = ArtworkEditAction.Replace(sampleArtwork, "image/jpeg")
            )
        )

        assertEquals("Silver for Monsters", written.title)
        assertEquals("Marcin Przybylowicz", written.artist)
        assertEquals("The Witcher 3 OST", written.album)
        assertEquals("CD Projekt Red", written.albumArtist)
        assertEquals("4", written.trackNumber)
        assertEquals("2", written.discNumber)
        assertEquals("2015", written.year)
        assertEquals("Soundtrack", written.genre)
        assertArrayEquals(sampleArtwork, written.artworkBytes)

        // Remove artwork and verify it's cleared while text tags remain intact.
        // Copy to a fresh file so Windows file-handle caching in jaudiotagger's Mp4TagWriter
        // does not block renameTo() on the same path during JVM unit tests.
        val copyForRemove = tempFolder.newFile("remove_${file.name}")
        file.copyTo(copyForRemove, overwrite = true)
        val afterRemove = dataSource.writeTagsToFile(
            copyForRemove,
            BatchTagEditRequest(artworkAction = ArtworkEditAction.Remove)
        )
        assertEquals("Silver for Monsters", afterRemove.title)
        assertNull(afterRemove.artworkBytes)
    }

    private fun createMinimalMp3(file: File): File {
        val out = ByteArrayOutputStream()
        // Empty ID3v2.3 header (10 bytes)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 0))
        // Three consecutive MPEG-1 Layer 3 128kbps 44.1kHz frames (417 bytes each) for reliable frame sync
        repeat(3) {
            out.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))
            out.write(ByteArray(413))
        }
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun createMinimalFlac(file: File): File {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        // Last metadata block flag (0x80) | STREAMINFO (0x00), length = 34 bytes
        out.write(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x22))
        val streamInfo = ByteArray(34)
        // min/max block size = 4096 (0x1000)
        streamInfo[0] = 0x10; streamInfo[1] = 0x00
        streamInfo[2] = 0x10; streamInfo[3] = 0x00
        // sample rate 44100 Hz (0x0AC44) in bits, 2 channels, 16 bps
        streamInfo[10] = 0x0A; streamInfo[11] = 0xC4.toByte(); streamInfo[12] = 0x42; streamInfo[13] = 0xF0.toByte()
        out.write(streamInfo)
        // Dummy audio frame padding so file size exceeds jaudiotagger's minimum file size check
        out.write(ByteArray(128))
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun createMinimalOggVorbis(file: File): File {
        val idPacket = ByteArrayOutputStream().apply {
            write(1)
            write("vorbis".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // version
            write(2) // channels
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(44100).array()) // sampleRate
            write(ByteArray(12)) // bitrates
            write(0xB8) // blocksize
            write(1) // framing
        }.toByteArray()

        val commentPacket = ByteArrayOutputStream().apply {
            write(3)
            write("vorbis".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4).array())
            write("test".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array())
            write(1)
        }.toByteArray()

        val setupPacket = ByteArrayOutputStream().apply {
            write(5)
            write("vorbis".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(0, 0, 0, 1))
        }.toByteArray()

        val out = ByteArrayOutputStream()
        out.write(buildOggPage(headerType = 0x02, granule = 0L, seq = 0, payload = idPacket))
        out.write(buildOggPage(headerType = 0x00, granule = 0L, seq = 1, payload = commentPacket))
        out.write(buildOggPage(headerType = 0x04, granule = 0L, seq = 2, payload = setupPacket))
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun buildOggPage(headerType: Int, granule: Long, seq: Int, payload: ByteArray): ByteArray {
        val page = ByteBuffer.allocate(27 + 1 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        page.put("OggS".toByteArray(Charsets.US_ASCII))
        page.put(0) // version
        page.put(headerType.toByte())
        page.putLong(granule)
        page.putInt(1) // serial
        page.putInt(seq)
        page.putInt(0) // crc placeholder
        page.put(1) // 1 segment
        page.put(payload.size.toByte())
        page.put(payload)
        val bytes = page.array()
        val crc = oggCrc32(bytes)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(22, crc)
        return bytes
    }

    private fun oggCrc32(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = (crc shl 8) xor OGG_CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
        }
        return crc
    }

    private fun createMinimalM4a(file: File): File {
        val ftyp = mp4Box("ftyp", "M4A ".toByteArray() + ByteArray(4) + "M4A ".toByteArray() + "mp42".toByteArray())
        val mvhdPayload = ByteArray(100).apply {
            ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(12, 1000) // timescale
                putInt(16, 1000) // duration
            }
            this[19] = 1
        }
        val mvhd = mp4Box("mvhd", mvhdPayload)
        val esdsPayload = ByteBuffer.allocate(34).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0) // version + flags
            put(0x03).put(28).putShort(1).put(0x00) // ES_Descriptor
            put(0x04).put(20).put(0x40).put(0x15).put(0).put(0).put(0).putInt(128000).putInt(128000) // DecoderConfig
            put(0x05).put(2).put(0x12).put(0x10) // AudioSpecificConfig: AAC-LC 44.1kHz 2ch
            put(0x06).put(1).put(0x02) // SLConfig
        }.array()
        val esds = mp4Box("esds", esdsPayload)
        val mp4aHeader = ByteArray(28).apply {
            ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(6, 1) // dataReferenceIndex
                putShort(16, 2) // channelCount
                putShort(18, 16) // sampleSize
                putInt(24, 44100 shl 16) // sampleRate 16.16 fixed-point
            }
        }
        val mp4a = mp4Box("mp4a", mp4aHeader + esds)
        val stsdPayload = ByteBuffer.allocate(8 + mp4a.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(0) // version + flags
            .putInt(1) // entryCount
            .put(mp4a)
            .array()
        val stsd = mp4Box("stsd", stsdPayload)
        val stts = mp4Box("stts", ByteArray(8))
        val stsc = mp4Box("stsc", ByteArray(8))
        val stsz = mp4Box("stsz", ByteArray(12))
        val stco = mp4Box("stco", ByteArray(8))
        val stbl = mp4Box("stbl", stsd + stts + stsc + stsz + stco)
        val smhd = mp4Box("smhd", ByteArray(8))
        val dinf = mp4Box("dinf", mp4Box("dref", ByteArray(8)))
        val minf = mp4Box("minf", smhd + dinf + stbl)
        val mdhdPayload = ByteArray(24).apply {
            ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(12, 44100) // timescale
                putInt(16, 44100) // duration
            }
        }
        val mdhd = mp4Box("mdhd", mdhdPayload)
        val hdlr = mp4Box("hdlr", ByteArray(25).apply { "soun".toByteArray().copyInto(this, 8) })
        val mdia = mp4Box("mdia", mdhd + hdlr + minf)
        val tkhd = mp4Box("tkhd", ByteArray(84))
        val trak = mp4Box("trak", tkhd + mdia)
        val metaHdlr = mp4Box("hdlr", ByteArray(25).apply { "mdir".toByteArray().copyInto(this, 8) })
        val ilst = mp4Box("ilst", ByteArray(0))
        val meta = mp4Box("meta", ByteArray(4) + metaHdlr + ilst)
        val udta = mp4Box("udta", meta)
        val moov = mp4Box("moov", mvhd + trak + udta)
        val mdat = mp4Box("mdat", ByteArray(16))
        file.writeBytes(ftyp + moov + mdat)
        return file
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
            .putInt(size)
            .put(type.toByteArray(Charsets.US_ASCII))
            .put(payload)
            .array()
    }

    companion object {
        private val OGG_CRC_TABLE = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) {
                r = if ((r and 0x80000000.toInt()) != 0) (r shl 1) xor 0x04C11DB7 else (r shl 1)
            }
            r
        }
    }
}
