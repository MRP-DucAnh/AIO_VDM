package lib.process

import android.os.*
import java.lang.ref.*

/**
 * A utility object for time-based operations including delays, countdowns, intervals,
 * and stopwatches. This class provides a simple interface for common timing tasks
 * using Android's CountDownTimer with automatic memory leak prevention through
 * WeakReferences.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Delayed Execution:</b> Execute code after a specified delay</li>
 *   <li><b>Countdown Timer:</b> Track remaining time with periodic updates</li>
 *   <li><b>Recurring Intervals:</b> Execute code at regular intervals indefinitely</li>
 *   <li><b>Stopwatch:</b> Measure elapsed time with periodic updates</li>
 *   <li><b>Memory Safety:</b> All listeners are stored in WeakReferences to prevent
 *       memory leaks when Activities or Fragments are destroyed</li>
 *   <li><b>Timer Management:</b> Ability to cancel any running timer</li>
 * </ul>
 *
 * <p><b>Typical Usage Examples:</b></p>
 * <pre>
 * // Example 1: Simple delay
 * CommonTimeUtils.delay(3000, object : CommonTimeUtils.OnTaskFinishListener {
 *     override fun afterDelay() {
 *         Log.d("Timer", "3 seconds have passed")
 *         showWelcomeMessage()
 *     }
 * })
 *
 * // Example 2: Countdown timer
 * val countdown = CommonTimeUtils.startCountDown(10000, 1000,
 *     object : CommonTimeUtils.OnCountDownListener {
 *         override fun onTick(millisUntilFinished: Long) {
 *             updateCountdownDisplay(millisUntilFinished / 1000)
 *         }
 *         override fun onFinish() {
 *             showTimerCompleteMessage()
 *         }
 *     })
 *
 * // Example 3: Recurring interval
 * val intervalTimer = CommonTimeUtils.setInterval(5000,
 *     object : CommonTimeUtils.OnIntervalListener {
 *         override fun onInterval() {
 *             refreshDataFromServer()
 *         }
 *     })
 *
 * // Example 4: Stopwatch
 * val stopwatch = CommonTimeUtils.startStopWatch(100,
 *     object : CommonTimeUtils.OnStopWatchListener {
 *         override fun onTick(elapsedTime: Long) {
 *             updateStopwatchDisplay(elapsedTime)
 *         }
 *     })
 *
 * // Cancel any timer when no longer needed
 * CommonTimeUtils.cancelTimer(countdown)
 * </pre>
 *
 * <p><b>Threading Notes:</b></p>
 * All timers execute callbacks on the main/UI thread as CountDownTimer posts
 * updates to the main looper. This makes it safe to update UI components directly
 * in the callbacks without needing additional thread handling.
 *
 * <p><b>Memory Management:</b></p>
 * All listener references are stored as WeakReferences, allowing the garbage
 * collector to clean up listeners if the parent component is destroyed. However,
 * it's still good practice to cancel timers in lifecycle callbacks (onStop,
 * onDestroy) to prevent unnecessary callbacks.
 *
 * @author Generated
 * @since 1.0
 */
object CommonTimeUtils {

	/**
	 * Logger instance for debugging and error tracking.
	 * Automatically configured with the object name for easy log filtering.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Executes a task after a specified delay.
	 * This method creates a one-shot timer that fires once after the specified
	 * duration and then stops automatically.
	 *
	 * <p><b>Note:</b> The delay timer uses the same value for both the total time
	 * and interval, meaning it only fires once (no intermediate ticks).</p>
	 *
	 * @param timeInMile Delay duration in milliseconds before the task executes
	 * @param listener Callback interface to be notified when the delay completes
	 * @return The started CountDownTimer instance, which can be used to cancel
	 *         the delay before it completes if needed
	 * @see #cancelTimer(CountDownTimer)
	 * @see OnTaskFinishListener#afterDelay()
	 */
	@JvmStatic
	fun delay(timeInMile: Int, listener: OnTaskFinishListener): CountDownTimer {
		val safeTaskRef = WeakReference(listener)
		return object : CountDownTimer(timeInMile.toLong(), timeInMile.toLong()) {
			override fun onTick(millisUntilFinished: Long) = Unit

			override fun onFinish() {
				safeTaskRef.get()?.afterDelay()
			}
		}.start()
	}

	/**
	 * Starts a countdown timer that ticks at regular intervals until completion.
	 * This is useful for implementing countdown displays, time-limited operations,
	 * or any scenario where you need to track remaining time.
	 *
	 * <p>The timer provides two types of callbacks:
	 * <ul>
	 *   <li><b>onTick:</b> Called repeatedly at each interval with the remaining time</li>
	 *   <li><b>onFinish:</b> Called once when the countdown reaches zero</li>
	 * </ul>
	 * </p>
	 *
	 * @param totalTime Total duration of the countdown in milliseconds
	 * @param interval Time in milliseconds between each tick callback
	 * @param listener Callback interface for receiving tick and finish events
	 * @return The started CountDownTimer instance for cancellation if needed
	 * @see #cancelTimer(CountDownTimer)
	 * @see OnCountDownListener#onTick(long)
	 * @see OnCountDownListener#onFinish()
	 */
	@JvmStatic
	fun startCountDown(
		totalTime: Long, interval: Long,
		listener: OnCountDownListener
	): CountDownTimer {
		val safeTaskRef = WeakReference(listener)
		return object : CountDownTimer(totalTime, interval) {
			override fun onTick(millisUntilFinished: Long) {
				safeTaskRef.get()?.onTick(millisUntilFinished)
			}

			override fun onFinish() {
				safeTaskRef.get()?.onFinish()
			}
		}.start()
	}

	/**
	 * Executes a task at regular intervals indefinitely.
	 * This method creates a timer that fires repeatedly at the specified interval
	 * without ever finishing automatically. It's ideal for periodic background tasks,
	 * UI updates, or polling operations.
	 *
	 * <p><b>Important:</b> This timer runs indefinitely until explicitly canceled.
	 * Always cancel the timer when it's no longer needed to prevent unnecessary
	 * resource usage and potential memory leaks.</p>
	 *
	 * <p><b>Use cases include:</b>
	 * <ul>
	 *   <li>Auto-saving user data every few seconds</li>
	 *   <li>Polling a server for new data</li>
	 *   <li>Updating a live clock or timer display</li>
	 *   <li>Checking connection status periodically</li>
	 * </ul>
	 * </p>
	 *
	 * @param interval Time in milliseconds between each interval callback
	 * @param listener Callback interface that receives interval events
	 * @return The started CountDownTimer instance, which must be canceled when
	 *         the interval is no longer needed
	 * @see #cancelTimer(CountDownTimer)
	 * @see OnIntervalListener#onInterval()
	 */
	@JvmStatic
	fun setInterval(interval: Long, listener: OnIntervalListener): CountDownTimer {
		val safeTaskRef = WeakReference(listener)
		return object : CountDownTimer(Long.MAX_VALUE, interval) {
			override fun onTick(millisUntilFinished: Long) {
				safeTaskRef.get()?.onInterval()
			}

			override fun onFinish() = Unit
		}.start()
	}

	/**
	 * Starts a stopwatch that measures elapsed time with periodic updates.
	 * Unlike a countdown timer, a stopwatch counts up from zero and continues
	 * indefinitely until canceled. It's perfect for measuring how long an
	 * operation takes, tracking user activity duration, or implementing a
	 * stopwatch feature.
	 *
	 * <p>The stopwatch calculates elapsed time based on the system clock when
	 * the timer starts, ensuring accurate time tracking even if the device
	 * experiences temporary lag or delayed callbacks.</p>
	 *
	 * <p><b>Important:</b> The stopwatch runs indefinitely until explicitly
	 * canceled. Always cancel the timer when it's no longer needed to prevent
	 * unnecessary resource usage.</p>
	 *
	 * <p><b>Use cases include:</b>
	 * <ul>
	 *   <li>Tracking time spent on a task or in a game level</li>
	 *   <li>Measuring performance of operations</li>
	 *   <li>Implementing a workout or meditation timer</li>
	 *   <li>Recording session durations</li>
	 * </ul>
	 * </p>
	 *
	 * @param interval Time in milliseconds between each tick callback.
	 *                 Smaller intervals provide smoother updates but consume more resources.
	 *                 Common values: 100ms for smooth display, 1000ms for simple displays.
	 * @param listener Callback interface that receives elapsed time updates
	 * @return The started CountDownTimer instance for cancellation when stopwatch
	 *         functionality is no longer needed
	 * @see #cancelTimer(CountDownTimer)
	 * @see OnStopWatchListener#onTick(long)
	 */
	@JvmStatic
	fun startStopWatch(interval: Long, listener: OnStopWatchListener): CountDownTimer {
		val safeTaskRef = WeakReference(listener)
		return object : CountDownTimer(Long.MAX_VALUE, interval) {
			private val startTime: Long = System.currentTimeMillis()

			override fun onTick(millisUntilFinished: Long) {
				val elapsedTime = System.currentTimeMillis() - startTime
				safeTaskRef.get()?.onTick(elapsedTime)
			}

			override fun onFinish() = Unit
		}.start()
	}

	/**
	 * Cancels an active timer to prevent further callbacks and release resources.
	 * This method safely handles null timers and can be called multiple times
	 * without side effects.
	 *
	 * <p><b>Best Practice:</b> Always cancel timers in lifecycle callbacks such as
	 * onStop(), onPause(), or onDestroy() to prevent memory leaks and unnecessary
	 * background processing.</p>
	 *
	 * @param timer The CountDownTimer instance to cancel. If null or already canceled,
	 *              this method does nothing.
	 * @see CountDownTimer#cancel()
	 */
	@JvmStatic
	fun cancelTimer(timer: CountDownTimer?) {
		timer?.cancel()
	}

	/**
	 * Listener interface for one-shot delay operations.
	 * Implement this interface to be notified when a delay completes.
	 */
	interface OnTaskFinishListener {

		/**
		 * Called when the specified delay period has elapsed.
		 * This callback executes on the main/UI thread.
		 */
		fun afterDelay()
	}

	/**
	 * Listener interface for countdown timer operations.
	 * Implement this interface to receive both intermediate tick updates
	 * and the final completion event.
	 */
	interface OnCountDownListener {

		/**
		 * Called periodically at each interval during the countdown.
		 * This callback executes on the main/UI thread.
		 *
		 * @param millisUntilFinished The remaining time in milliseconds until
		 *                            the countdown finishes. This value decreases
		 *                            with each tick until reaching zero.
		 */
		fun onTick(millisUntilFinished: Long)

		/**
		 * Called once when the countdown reaches zero.
		 * This callback executes on the main/UI thread.
		 */
		fun onFinish()
	}

	/**
	 * Listener interface for recurring interval operations.
	 * Implement this interface to be notified at regular intervals indefinitely.
	 */
	interface OnIntervalListener {

		/**
		 * Called at each interval as specified in setInterval().
		 * This callback executes on the main/UI thread.
		 *
		 * <p><b>Note:</b> This callback continues indefinitely until the timer
		 * is explicitly canceled.</p>
		 */
		fun onInterval()
	}

	/**
	 * Listener interface for stopwatch operations.
	 * Implement this interface to receive elapsed time updates.
	 */
	interface OnStopWatchListener {

		/**
		 * Called periodically at each interval with the current elapsed time.
		 * This callback executes on the main/UI thread.
		 *
		 * @param elapsedTime The total time elapsed since the stopwatch started,
		 *                    in milliseconds. This value increases with each tick.
		 */
		fun onTick(elapsedTime: Long)
	}
}