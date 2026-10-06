package org.torproject.android.localization

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.torproject.android.util.Prefs
import java.util.*

/**
 * This class is used to change your application locale and persist this change for the next time
 * that your app is going to be used.
 *
 * You can also change the locale of your application on the fly by using the setLocale method.
 *
 * Created by gunhansancar on 07/10/15.
 * https://gunhansancar.com/change-language-programmatically-in-android/
 */
object LocaleHelper {
    fun onAttach(context: Context): Context = setLocale(context, Prefs.defaultLocale)

    /**
     * Pol: on Android 13+ the activity is created with whatever language the system has for the
     * app, and OrbotActivity handles locale changes itself (configChanges="locale"), so it never
     * picks up a language set at runtime. Wrap the base context instead: the per-app language the
     * user chose in system settings if there is one, otherwise Pol's default (Persian).
     */
    fun wrapForPerAppLanguage(context: Context): Context {
        var tag: String? = null
        try {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            val list = lm?.applicationLocales
            if (list != null && !list.isEmpty) tag = list.get(0).toLanguageTag()
        } catch (_: Throwable) {
        }
        val locale = Locale.forLanguageTag(tag ?: Prefs.defaultLocale.replace('_', '-'))
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun setLocale(context: Context, language: String): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context
        }
        Prefs.defaultLocale = language
        return updateResources(context, language)
    }

    private fun updateResources(context: Context, locale: String): Context {

        var language = locale
        var region = ""

        if (language.contains("_")) {
            val parts = locale.split("_")
            language = parts[0]
            region = parts[1]
        }

        val localeObj = Languages.buildLocaleForLanguage(language, region)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(localeObj))
        Locale.setDefault(localeObj)
        val configuration = context.resources.configuration
        configuration.setLocale(localeObj)
        configuration.setLayoutDirection(localeObj)
        return context.createConfigurationContext(configuration)
    }
}