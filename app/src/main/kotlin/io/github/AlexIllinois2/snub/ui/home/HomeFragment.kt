package io.github.AlexIllinois2.snub.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.github.AlexIllinois2.snub.app.AppInfo
import io.github.AlexIllinois2.snub.app.HailData.tags
import io.github.AlexIllinois2.snub.databinding.FragmentHomeBinding
import io.github.AlexIllinois2.snub.extensions.applyDefaultInsetter
import io.github.AlexIllinois2.snub.extensions.isLandscape
import io.github.AlexIllinois2.snub.extensions.isRtl
import io.github.AlexIllinois2.snub.extensions.paddingRelative
import io.github.AlexIllinois2.snub.ui.main.MainFragment
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
            tab.text = tags[position].name
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