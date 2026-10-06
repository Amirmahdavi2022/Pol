package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import org.torproject.android.R
import org.torproject.android.databinding.FragmentTestingConfirmBinding
import org.torproject.android.ui.TransparentWindowDialogFragment
import org.torproject.android.util.dismissAllDialogFragments

class ActivateDialogConfirm : TransparentWindowDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTestingConfirmBinding.inflate(inflater)
        binding.btnCancel.setOnClickListener {
            requireActivity().supportFragmentManager.dismissAllDialogFragments()
        }
        binding.swAcknowledge.setOnCheckedChangeListener { _, bool ->
            binding.btnContinue.apply {
                isEnabled = bool
                backgroundTintList = ContextCompat.getColorStateList(
                    requireContext(),
                    if (bool) R.color.orbot_btn_enabled_purple else R.color.orbot_btn_disable_grey
                )
            }
        }
        binding.btnContinue.setOnClickListener {
            requireActivity().supportFragmentManager.apply {
                dismissAllDialogFragments()
                TestingDialogFragment.show(this)
            }
        }
        return binding.root
    }

    companion object {
        fun show(fragmentManager: FragmentManager) =
            ActivateDialogConfirm().show(fragmentManager, "FragmentConfirm")
    }
}