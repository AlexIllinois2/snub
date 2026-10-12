package io.github.AlexIllinois2.snub.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.github.AlexIllinois2.snub.app.HailData

class HomeAdapter(fragment: HomeFragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = HailData.displayTags.size

    override fun createFragment(position: Int): Fragment = PagerFragment()
}