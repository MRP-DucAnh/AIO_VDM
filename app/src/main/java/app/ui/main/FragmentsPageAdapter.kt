@file:Suppress("DEPRECATION")

package app.ui.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.core.bases.BaseActivity
import app.ui.main.fragments.browser.BrowserFragment
import app.ui.main.fragments.downloads.DownloadsFragment
import app.ui.main.fragments.home.HomeFragment
import app.ui.main.fragments.settings.SettingsFragment

class FragmentsPageAdapter(baseActivity: BaseActivity) :
	FragmentStateAdapter(baseActivity) {

	override fun getItemCount(): Int = 4

	override fun createFragment(position: Int): Fragment {
		return when (position) {
			0 -> HomeFragment()
			1 -> BrowserFragment()
			2 -> DownloadsFragment()
			3 -> SettingsFragment()
			else -> HomeFragment()
		}
	}
}