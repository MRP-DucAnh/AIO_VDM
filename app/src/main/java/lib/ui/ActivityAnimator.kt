@file:Suppress("DEPRECATION")

package lib.ui

import android.app.*
import androidx.core.app.*
import app.core.bases.*
import com.aio.*
import lib.process.*
import java.lang.ref.*

/**
 * A utility singleton providing a centralized suite of activity transition animations.
 *
 * This object simplifies the application of custom `overridePendingTransition` logic
 * across the application. It contains both synchronous and asynchronous (suspend)
 * methods to handle various navigation styles—such as swipes, slides, and fades—ensuring
 * a consistent and fluid user experience.
 *
 * Most methods within this object utilize [withMainContext] or [WeakReference]
 * patterns to ensure thread safety and prevent memory leaks, making it suitable for
 * use within modern coroutine-based architectures.
 */
object ActivityAnimator {

	/**
	 * Applies a smooth "Fade" transition animation to the provided activity on the Main thread.
	 *
	 * This animation provides a subtle cross-fade effect where the incoming screen gradually
	 * becomes opaque while the outgoing screen becomes transparent. By utilizing [withMainContext],
	 * this function ensures that the transition override is executed safely on the UI thread,
	 * even when called from an asynchronous coroutine.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivityFade(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_fade_enter,
				R.anim.anim_fade_exit
			)
		}
	}

	/**
	 * Applies a custom "In and Out" scaling or specialized transition to the provided activity
	 * on the Main thread.
	 *
	 * This animation typically involves the new activity expanding "in" while the current
	 * activity transitions "out," often used for emphasizing the depth of navigation or
	 * opening specific media components. The [withMainContext] wrapper guarantees
	 * that the framework's transition logic is invoked on the appropriate thread.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivityInAndOut(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_in_out_enter,
				R.anim.anim_in_out_exit
			)
		}
	}

	/**
	 * Applies a custom "Slide Down" transition animation to the provided activity on the Main thread.
	 *
	 * This animation is typically used when dismissing a modal-style screen or a top-down
	 * overlay, causing the current content to move toward the bottom of the display.
	 * Wrapping the logic in [withMainContext] ensures the transition is safely
	 * handled by the UI thread.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySlideDown(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_down_enter,
				R.anim.anim_slide_down_exit
			)
		}
	}

	/**
	 * Applies a custom "Slide Left" transition animation to the provided activity on the Main thread.
	 *
	 * This motion creates a horizontal transition where content slides toward the left side
	 * of the screen. The [withMainContext] block guarantees that the framework's
	 * [Activity.overridePendingTransition] is called on the appropriate thread to
	 * prevent rendering glitches.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySlideLeft(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_left_enter,
				R.anim.anim_slide_left_exit
			)
		}
	}

	/**
	 * Applies a custom "Swipe Right" transition animation to the provided activity on the Main thread.
	 *
	 * This animation is commonly used for "Back" gestures or navigating to a previous
	 * screen in a sequence, with content entering from the left. Using [withMainContext]
	 * ensures this UI-specific operation is safely performed when called from a
	 * background coroutine scope.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySwipeRight(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_swipe_right_enter,
				R.anim.anim_swipe_right_exit
			)
		}
	}

	/**
	 * Applies a custom "Slide Up" transition animation to the provided activity on the Main thread.
	 *
	 * This animation is typically used for presenting a new screen as a modal or bottom-up
	 * overlay. By wrapping the transition in [withMainContext], this function ensures
	 * thread safety when triggered from background coroutines.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySlideUp(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_up_enter,
				R.anim.anim_slide_up_exit
			)
		}
	}

	/**
	 * Applies a custom "Swipe Left" transition animation to the provided activity on the Main thread.
	 *
	 * This motion is generally used for forward navigation where the new content enters
	 * from the right side. The use of [withMainContext] guarantees the UI transition is
	 * executed on the appropriate thread.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySwipeLeft(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_swipe_left_enter,
				R.anim.anim_swipe_left_exit
			)
		}
	}

	/**
	 * Applies a custom "Slide Right" transition animation to the provided activity on the Main thread.
	 *
	 * This transition creates a backward navigation effect, where the incoming screen slides
	 * in from the left. Execution is forced onto the Main thread via [withMainContext]
	 * to ensure UI stability.
	 *
	 * @param activity The [BaseActivity] instance providing the context to override
	 * pending transitions.
	 */
	@JvmStatic
	suspend fun animActivitySlideRight(activity: BaseActivity?) {
		withMainContext {
			activity?.getActivity()?.overridePendingTransition(
				R.anim.anim_slide_in_left,
				R.anim.anim_slide_out_right
			)
		}
	}

	/**
	 * Creates a set of transition options to perform a horizontal sliding animation
	 * during an Activity transition.
	 *
	 * This function utilizes a [WeakReference] to the provided [Activity] to prevent
	 * memory leaks if the animation options outlive the activity's lifecycle. It
	 * leverages standard Android framework resources to achieve a "Slide In Left"
	 * and "Slide Out Right" motion, providing a familiar Material Design navigation
	 * feel.
	 *
	 * @param activity The [Activity] context required to load and initialize
	 * the animation resources.
	 * @return An [ActivityOptionsCompat] object containing the animation bundle,
	 * or null if the activity context is no longer valid or was not provided.
	 */
	@JvmStatic
	suspend fun getMaterialSlideOptions(activity: BaseActivity?): ActivityOptionsCompat? {
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