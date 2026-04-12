package lib.ui.builders

import android.annotation.*
import android.content.*
import android.util.*
import android.view.*
import androidx.viewpager.widget.*

/**
 * A custom [ViewPager] implementation that disables manual swiping gestures.
 *
 * This class overrides touch event handling to prevent users from navigating between
 * pages by dragging or swiping. Navigation can still be performed programmatically
 * using [setCurrentItem].
 *
 * ### Use Cases:
 * * Form wizards where validation is required before proceeding.
 * * Navigation where transitions are strictly controlled by external buttons.
 * * Preventing accidental swipes in complex nested scroll layouts.
 *
 * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
 * @param attrs The attributes of the XML tag that is inflating the view.
 */
class NonSwappableViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {

	/**
	 * Intercepts all touch screen motion events.
	 * By returning `false`, we prevent the ViewPager from "stealing" the touch event
	 * to initiate a swipe scroll.
	 *
	 * @param ev The motion event being dispatched down the hierarchy.
	 * @return Always returns `false` to disable swipe interception.
	 */
	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

	/**
	 * Handles touch screen motion events.
	 * By returning `false`, we signify that this view does not handle the touch,
	 * effectively disabling the scrolling response to a user's finger movement.
	 *
	 * @param ev The motion event.
	 * @return Always returns `false` to ignore touch interactions.
	 */
	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent): Boolean = false
}