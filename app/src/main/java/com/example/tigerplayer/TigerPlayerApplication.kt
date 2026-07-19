package com.example.tigerplayer

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TigerPlayerApplication : Application() {

	override fun onCreate() {
		super.onCreate()
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
}
