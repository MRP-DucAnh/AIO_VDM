package lib.ui.listeners

import android.widget.*
import android.widget.SeekBar.*

/**
 * A boilerplate-reduction class for [SeekBar.OnSeekBarChangeListener] that simplifies
 * progress tracking.
 *
 * This abstract class provides a cleaner interface for monitoring [SeekBar] interactions
 * by providing empty default implementations for touch tracking callbacks. By extending
 * this class, developers can focus solely on implementing the [onProgressChange]
 * logic without the need to override every method in the listener interface.
 */
abstract class SeekBarListener : OnSeekBarChangeListener {

	/**
	 * Invoked when the progress level of the [SeekBar] has changed.
	 * * This abstract method provides a cleaner signature for reacting to progress updates.
	 * It allows the implementation to distinguish between manual user adjustments
	 * and programmatic changes via the [fromUser] flag.
	 *
	 * @param seekBar The [SeekBar] whose progress has changed.
	 * @param progress The current progress level, scaled between the min and max values.
	 * @param fromUser True if the progress change was initiated by a user touch gesture.
	 */
	abstract fun onProgressChange(seekBar: SeekBar?, progress: Int, fromUser: Boolean)

	/**
	 * Internal proxy that routes the standard [SeekBar.OnSeekBarChangeListener] callback
	 * to the simplified [onProgressChange] method.
	 */
	override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
		onProgressChange(seekBar, progress, fromUser)
	}

	/**
	 * Default implementation that ignores the notification that the user has started
	 * a touch gesture. Override this if you need to pause background updates or
	 * change the UI state during tracking.
	 */
	override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

	/**
	 * Default implementation that ignores the notification that the user has finished
	 * a touch gesture. Override this if you need to commit changes or resume
	 * updates once the user releases the seek bar.
	 */
	override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
}