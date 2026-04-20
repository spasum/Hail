package io.spasum.hailshizuku.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.spasum.hailshizuku.app.HailData.tags

class HomeAdapter(fragment: HomeFragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = tags.size

    override fun createFragment(position: Int): Fragment = PagerFragment()
}