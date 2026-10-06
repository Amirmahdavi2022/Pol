package org.torproject.android.ui

import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

open class TransparentWindowDialogFragment : DialogFragment() {
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            dialog?.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    }
}