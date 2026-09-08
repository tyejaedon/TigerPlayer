package com.example.tigerplayer

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.example.tigerplayer.data.repository.StatsEpoch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TigerPlayerApplication : Application() {

	@Inject
	lateinit var statsEpoch: StatsEpoch

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onCreate() {
		super.onCreate()

		// Hilt field injection completes during super.onCreate(), so statsEpoch is safe to use here.
		// One-time, idempotent: establishes the point from which analytics are trustworthy (#80).
		applicationScope.launch {
			runCatching { statsEpoch.initializeIfNeeded() }
				.onFailure { Log.e(TAG, "Failed to initialize stats epoch", it) }
		}

		if (BuildConfig.DEBUG) {
			installStrictMode()
		}
	}

	private fun installStrictMode() {
		StrictMode.setThreadPolicy(
			StrictMode.ThreadPolicy.Builder()
				.detectDiskReads()
				.detectDiskWrites()
				.detectNetwork()
				.penaltyLog()
				.build()
		)

		StrictMode.setVmPolicy(
			StrictMode.VmPolicy.Builder()
				.detectLeakedClosableObjects()
				.detectLeakedRegistrationObjects()
				.detectActivityLeaks()
				.detectFileUriExposure()
				.penaltyLog()
				.build()
		)
	}

	private companion object {
		const val TAG = "TigerPlayerApplication"
	}
}
