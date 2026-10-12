package com.tigerplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDataStoreTest {

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return lock.withLock {
                val updated = transform(state.value)
                state.value = updated
                updated
            }
        }
    }

    @Test
    fun persists_and_reads_control_matrix_preferences() {
        runBlocking {
            val settingsDataStore = SettingsDataStore(InMemoryPreferencesDataStore())

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
        }
    }

    @Test
    fun persists_cover_screen_volume_and_eq_preset_with_safe_coercion() {
        runBlocking {
            val settingsDataStore = SettingsDataStore(InMemoryPreferencesDataStore())

            settingsDataStore.setCoverScreenVolume(0.64f)
            settingsDataStore.setCoverScreenEqPreset(CoverScreenEqPreset.NIGHT_DRIVE)
            val persisted = settingsDataStore.settingsFlow.first()

            assertEquals(0.64f, persisted.coverScreenVolume, 0.001f)
            assertEquals(CoverScreenEqPreset.NIGHT_DRIVE, persisted.coverScreenEqPreset)

            settingsDataStore.setCoverScreenVolume(Float.NaN)
            assertEquals(1f, settingsDataStore.settingsFlow.first().coverScreenVolume, 0f)

            settingsDataStore.setCoverScreenVolume(1.5f)
            assertEquals(1f, settingsDataStore.settingsFlow.first().coverScreenVolume, 0f)
        }
    }

    @Test
    fun falls_back_for_unknown_cover_screen_eq_preset_and_invalid_volume() {
        runBlocking {
            val initial = mutablePreferencesOf(
                floatPreferencesKey("cover_screen_volume") to Float.NaN,
                stringPreferencesKey("cover_screen_eq_preset") to "FUTURE_PRESET"
            )
            val settingsDataStore = SettingsDataStore(InMemoryPreferencesDataStore(initial))

            val snapshot = settingsDataStore.settingsFlow.first()

            assertEquals(1f, snapshot.coverScreenVolume, 0f)
            assertEquals(CoverScreenEqPreset.NEURAL_ADAPTIVE, snapshot.coverScreenEqPreset)
        }
    }
}
