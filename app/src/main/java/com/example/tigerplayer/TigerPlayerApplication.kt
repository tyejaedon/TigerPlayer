package com.example.tigerplayer

import android.app.Application
import android.os.StrictMode
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.tigerplayer.data.remote.NavidromeArtInterceptor
import com.example.tigerplayer.data.remote.NavidromeUrlSigner
import com.example.tigerplayer.data.repository.StatsEpoch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TigerPlayerApplication : Application(), ImageLoaderFactory {

	@Inject
	lateinit var statsEpoch: StatsEpoch

	@Inject
	lateinit var navidromeUrlSigner: NavidromeUrlSigner

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

	/**
	 * Coil loader that resolves opaque `navidrome://art/<id>` URIs into freshly signed URLs at
	 * request time (issue #44).
	 *
	 * Artwork URIs are persisted alongside tracks, so like stream URIs they must not carry a
	 * rotating token. Signing in an interceptor keeps every `AsyncImage` call site unchanged.
	 */
	override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
		.components {
			add(NavidromeArtInterceptor(navidromeUrlSigner))
		}
		.build()

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
