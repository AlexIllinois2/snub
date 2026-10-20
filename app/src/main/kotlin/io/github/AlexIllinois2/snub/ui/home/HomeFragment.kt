package io.github.AlexIllinois2.snub.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.AlexIllinois2.snub.app.AppInfo
import io.github.AlexIllinois2.snub.app.HailData
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
    /**
     * Nullable accessor for callbacks that may outlive the view (delayed posts,
     * coroutines): [binding] would throw once the view is destroyed.
     */
    val bindingOrNull: FragmentHomeBinding? get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.pager.adapter = HomeAdapter(this)
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = HailData.displayTags[position].name
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