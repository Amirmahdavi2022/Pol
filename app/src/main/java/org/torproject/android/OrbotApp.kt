package org.torproject.android

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.torproject.android.localization.Languages
import org.torproject.android.localization.LocaleHelper
import org.torproject.android.service.circumvention.Transport.Companion.stateLocation
import org.torproject.android.util.Prefs

import java.util.Locale

class OrbotApp : Application() {


    override fun onCreate() {
        super.onCreate()

        // set state dir for IPtProxy
        try {
            stateLocation = cacheDir.path
        } catch (_: Exception) {
            Log.e("OrbotApp", "Couldn't set PT state dir")
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                if (!isAuthenticationPromptOpenLegacyFlag)
                    shouldRequestAuthentication = true
            }

        })

//      useful for finding unclosed sockets...
//        StrictMode.setVmPolicy(
//            VmPolicy.Builder()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .build()
//        )

        Prefs.setContext(applicationContext)
        Prefs.orbotServiceLogClear()
        applyPolDefaultLanguage()
        LocaleHelper.onAttach(applicationContext)

        Languages.setup(OrbotActivity::class.java, R.string.menu_settings)

        setLocale()

        // this code only runs on first install and app updates
        if (Prefs.currentVersionForUpdate < BuildConfig.VERSION_CODE) {
            Prefs.currentVersionForUpdate = BuildConfig.VERSION_CODE
            // don't do anything resource intensive here, instead set a flag to do the task later

            // tell OrbotService it needs to reinstall geoip
            Prefs.isGeoIpReinstallNeeded = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setLocale()
    }

    /**
     * Pol: Android 13+ ignores the in-app locale helper and uses the per-app language the system
     * stores. Set Persian once on first run; after that the user's own choice in system settings wins.
     */
    private fun applyPolDefaultLanguage() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("pol", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("locale_init", false)) return
        try {
            val lm = getSystemService(android.app.LocaleManager::class.java)
            if (lm != null && lm.applicationLocales.isEmpty) {
                lm.applicationLocales = android.os.LocaleList.forLanguageTags("fa")
            }
        } catch (_: Exception) {
        }
        prefs.edit().putBoolean("locale_init", true).apply()
    }

    fun setLocale() {
        val appLocale = Prefs.defaultLocale
        val systemLoc = Locale.getDefault().language
        if (appLocale != systemLoc) {
            Languages.setLanguage(this, appLocale, true)
        }
    }

    companion object {
        var shouldRequestAuthentication: Boolean = true

        // see https://github.com/guardianproject/orbot-android/issues/1340
        var isAuthenticationPromptOpenLegacyFlag: Boolean = false
        fun resetLockFlags() {
            shouldRequestAuthentication = true
            isAuthenticationPromptOpenLegacyFlag = false
        }
    }
}
