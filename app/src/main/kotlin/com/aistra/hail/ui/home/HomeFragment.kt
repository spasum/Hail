package io.spasum.hailshizuku.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.spasum.hailshizuku.app.AppInfo
import io.spasum.hailshizuku.app.HailData.tags
import io.spasum.hailshizuku.databinding.FragmentHomeBinding
import io.spasum.hailshizuku.extensions.applyDefaultInsetter
import io.spasum.hailshizuku.extensions.isLandscape
import io.spasum.hailshizuku.extensions.isRtl
import io.spasum.hailshizuku.extensions.paddingRelative
import io.spasum.hailshizuku.ui.main.MainFragment
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : MainFragment() {
    var multiselect: Boolean = false
    val selectedList: MutableList<AppInfo> = mutableListOf()
    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        if (tags.size == 1) binding.tabs.isVisible = false
        binding.pager.adapter = HomeAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = tags[position].first
        }.attach()
        binding.tabs.applyDefaultInsetter { paddingRelative(isRtl, start = !activity.isLandscape, end = true) }
        return binding.root
    }

    override fun onDestroyView() {
        multiselect = false
        selectedList.clear()
        super.onDestroyView()
        _binding = null
    }
}