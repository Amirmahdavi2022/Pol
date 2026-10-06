package org.torproject.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

class SecureListPreference(context: Context, attrs: AttributeSet?) :
    ListPreference(context, attrs) {

    init {
        preferenceDataStore = SecurePreferenceDataStore()
    }
}