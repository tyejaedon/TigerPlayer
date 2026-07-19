package com.example.tigerplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDataStoreTest {

    @Test
    fun persists_and_reads_control_matrix_preferences() {
        runBlocking {
            val tempFile = File.createTempFile("settings_test", ".preferences_pb")
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                produceFile = { tempFile }
            )

            val settingsDataStore = SettingsDataStore(dataStore)

            settingsDataStore.setThemeMode(ThemeMode.DARK)
            settingsDataStore.setPureAmoledBlack(true)
            settingsDataStore.setAccentStyle(TigerAccentStyle.TOXIC_LIME)
            settingsDataStore.setDefaultPlayerView(DefaultPlayerView.SONIC_PRISM)
            settingsDataStore.setCrossfadeDurationSec(9)
            settingsDataStore.setGaplessPlayback(false)
            settingsDataStore.setAudioReactiveHaptics(true)
            settingsDataStore.setSkipShortAudio(SkipShortAudio.BELOW_60_SECONDS)
            settingsDataStore.setResumeOnBluetoothConnect(false)
            settingsDataStore.setResumeOnWiredHeadsetConnect(true)

            val snapshot = settingsDataStore.settingsFlow.first()

            assertEquals(ThemeMode.DARK, snapshot.themeMode)
            assertEquals(true, snapshot.pureAmoledBlack)
            assertEquals(TigerAccentStyle.TOXIC_LIME, snapshot.accentStyle)
            assertEquals(DefaultPlayerView.SONIC_PRISM, snapshot.defaultPlayerView)
            assertEquals(9, snapshot.crossfadeDurationSec)
            assertEquals(false, snapshot.gaplessPlayback)
            assertEquals(true, snapshot.audioReactiveHaptics)
            assertEquals(SkipShortAudio.BELOW_60_SECONDS, snapshot.skipShortAudio)
            assertEquals(false, snapshot.resumeOnBluetoothConnect)
            assertEquals(true, snapshot.resumeOnWiredHeadsetConnect)

            tempFile.delete()
        }
    }
}

