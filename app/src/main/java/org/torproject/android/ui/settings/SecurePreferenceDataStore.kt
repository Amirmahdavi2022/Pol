package org.torproject.android.ui.settings

import androidx.preference.PreferenceDataStore
import org.torproject.android.util.Settings

class SecurePreferenceDataStore: PreferenceDataStore() {
    override fun putString(key: String, value: String?) {
        Settings.set(key, value)
    }

    override fun getString(key: String, defValue: String?): String? {
        return Settings.get(key) ?: defValue
    }
}