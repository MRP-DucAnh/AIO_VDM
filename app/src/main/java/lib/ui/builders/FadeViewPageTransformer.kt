package lib.ui.builders

import androidx.viewpager.widget.ViewPager

/**
 * A [ViewPager.PageTransformer] implementation that provides a cross-fading transition effect.
 *
 * This transformer overrides the default sliding animation by negating the horizontal
 * translation of the pages, causing them to overlap at the same position. It then adjusts
 * the alpha transparency of each page based on its distance from the center, creating
 * a smooth fade-in and fade-out transition.
 */
class FadeViewPageTransformer : ViewPager.PageTransformer {

	/**
	 * Applies a fade transformation to the page, neutralizing the default sliding transition.
	 *
	 * As the page moves away from the center (position 0), its alpha decreases towards 0.
	 * The `translationX` is adjusted to counteract the default [ViewPager] scroll,
	 * keeping the pages stacked on top of each other for a smooth cross-fade effect.
	 *
	 * @param page The [android.view.View] to apply the transformation to.
	 * @param position The relative position of the page. `0` is centered, `1` is one full
	 * page to the right, and `-1` is one full page to the left.
	 */
	override fun transformPage(page: android.view.View, position: Float) {
		page.apply {
			when {
				// Page is way off-screen to the left.
				position < -1 -> {
					alpha = 0f
				}

				// Page is visible. Fade it based on its position.
				position <= 1 -> {
					alpha = 1 - kotlin.math.abs(position)
					translationX = page.width * -position
				}

				// Page is way off-screen to the right.
				else -> {
					alpha = 0f
				}
			}
		}
	}
}
