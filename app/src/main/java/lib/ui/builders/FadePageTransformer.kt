package lib.ui.builders

import androidx.viewpager.widget.ViewPager

class FadePageTransformer : ViewPager.PageTransformer {

	override fun transformPage(page: android.view.View, position: Float) {
		when {
			position < -1 -> {
				page.alpha = 0f
			}
			position <= 1 -> {
				page.alpha = 1 - kotlin.math.abs(position)
				page.translationX = page.width * -position
			}
			else -> {
				page.alpha = 0f
			}
		}
	}
}