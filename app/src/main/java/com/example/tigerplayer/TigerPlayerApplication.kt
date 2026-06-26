package com.example.tigerplayer

import android.app.Application
import android.os.StrictMode
import com.example.tigerplayer.debug.LeakCanaryInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TigerPlayerApplication : Application() {

	override fun onCreate() {
		super.onCreate()
		if (BuildConfig.DEBUG) {
			installStrictMode()
			LeakCanaryInitializer.install(this)
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
}
