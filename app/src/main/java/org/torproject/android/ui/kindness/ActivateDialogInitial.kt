package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.torproject.android.databinding.FragmentTestingInitialBinding
import org.torproject.android.ui.TransparentWindowDialogFragment

class ActivateDialogInitial : TransparentWindowDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTestingInitialBinding.inflate(inflater)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnContinue.setOnClickListener { ActivateDialogConfirm.show(requireActivity().supportFragmentManager) }
        return binding.root
    }

    companion object {
        fun show(fragmentManager: FragmentManager) =
            ActivateDialogInitial().show(fragmentManager, "TestingDialogInitial")
    }
}