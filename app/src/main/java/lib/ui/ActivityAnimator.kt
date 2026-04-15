@file:Suppress("DEPRECATION")

package lib.ui

import androidx.core.app.*
import app.core.bases.*
import com.aio.*
import lib.process.*

object ActivityAnimator {

	@JvmStatic
	suspend fun animActivityFade(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_fade_enter,
				R.anim.anim_fade_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivityInAndOut(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_in_out_enter,
				R.anim.anim_in_out_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySlideDown(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_down_enter,
				R.anim.anim_slide_down_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySlideLeft(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_left_enter,
				R.anim.anim_slide_left_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySwipeRight(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_swipe_right_enter,
				R.anim.anim_swipe_right_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySlideUp(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_up_enter,
				R.anim.anim_slide_up_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySwipeLeft(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_swipe_left_enter,
				R.anim.anim_swipe_left_exit
			)
		}
	}

	@JvmStatic
	suspend fun animActivitySlideRight(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_in_left,
				R.anim.anim_slide_out_right
			)
		}
	}

	@JvmStatic
	suspend fun getMaterialSlideOptions(activity: BaseActivity?)
		: ActivityOptionsCompat? {
		return withMainContext {
			activity?.getActivity()?.let { activityRef ->
				ActivityOptionsCompat.makeCustomAnimation(
					activityRef,
					android.R.anim.slide_in_left,
					android.R.anim.slide_out_right
				)
			} ?: run { null }
		}
	}
}