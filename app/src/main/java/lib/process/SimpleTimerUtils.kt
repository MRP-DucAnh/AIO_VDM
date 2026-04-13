package lib.process

import android.os.*
import java.lang.ref.*

/**
 * A utility class for managing countdown timers with pause, resume, and update capabilities.
 * 
 * This class wraps Android's CountDownTimer to provide enhanced functionality including:
 * - Pause and resume operations
 * - Dynamic time remaining updates
 * - Weak reference listener pattern to prevent memory leaks
 * - Running state tracking
 * - Default empty implementations for tick and finish callbacks
 * 
 * The timer can be started, paused, resumed, canceled, and have its total duration updated
 * even while running. The class maintains the remaining time accurately across pause/resume
 * operations.
 * 
 * Memory management: The class uses WeakReference for the TimerListener to avoid memory
 * leaks when activities or fragments hold references to this timer. Always ensure you're
 * not holding strong references to the timer from long-lived objects.
 * 
 * Usage example:
 * ```kotlin
 * val timer = SimpleTimerUtils(30000, 1000) // 30 seconds, tick every second
 * timer.setTimerListener(object : SimpleTimerUtils.TimerListener {
 *     override fun onTick(millisUntilFinished: Long) {
 *         updateUI(millisUntilFinished)
 *     }
 *     override fun onFinish() {
 *         timerComplete()
 *     }
 * })
 * timer.start()
 * 
 * // Later, to pause/resume:
 * timer.pause()
 * timer.resume()
 * ```
 * 
 * @param millisInFuture The total duration of the timer in milliseconds. Must be positive.
 * @param countDownInterval The interval between onTick callbacks in milliseconds.
 *                          Must be positive and typically 1000ms for second-based updates.
 */
class SimpleTimerUtils(
	private var millisInFuture: Long,
	private val countDownInterval: Long
) {

	/**
	 * The current amount of time remaining on the timer in milliseconds.
	 * This value is updated during timer operation and persists across pause/resume.
	 * 
	 * When the timer finishes, this is reset to millisInFuture.
	 * When canceled, this is reset to millisInFuture.
	 * When updated via updateTime(), this is set to the new millisInFuture value.
	 */
	var timeRemaining: Long = millisInFuture

	/**
	 * Indicates whether the timer is currently actively counting down.
	 * 
	 * This property is true when the timer is running and counting down.
	 * It is false when the timer is paused, canceled, finished, or hasn't been started.
	 * 
	 * Note: isRunning and isPaused are mutually exclusive - when one is true, the other is false.
	 */
	var isRunning: Boolean = false

	/**
	 * Indicates whether the timer has been paused but not canceled.
	 * 
	 * When true, the timer has been started and then paused, and can be resumed
	 * from where it left off. The timeRemaining property holds the current value.
	 * 
	 * Note: isPaused and isRunning are mutually exclusive - when one is true, the other is false.
	 */
	var isPaused: Boolean = false

	/**
	 * Weak reference to the timer listener to prevent memory leaks.
	 * 
	 * Using WeakReference allows the listener (typically an Activity or Fragment) to be
	 * garbage collected even if the timer is still referenced elsewhere, preventing
	 * common memory leak scenarios where the timer outlives its listener.
	 * 
	 * The weak reference is null when no listener is set or the listener has been collected.
	 */
	private var weakListener: WeakReference<TimerListener>? = null

	/**
	 * The underlying Android CountDownTimer instance.
	 * 
	 * This is null when the timer is not running (paused, canceled, finished, or not started).
	 * A new instance is created each time the timer is started or resumed.
	 */
	private var countDownTimer: CountDownTimer? = null

	/**
	 * Creates and starts a new CountDownTimer instance with the current timeRemaining value.
	 * 
	 * This method is called internally when starting or resuming the timer. It creates
	 * an anonymous CountDownTimer subclass that delegates callbacks to both the internal
	 * handlers and the external listener.
	 * 
	 * The timer uses the current timeRemaining (which may have been reduced by previous
	 * run time) and the configured countDownInterval.
	 * 
	 * Callback flow:
	 * 1. onTick: Updates timeRemaining, calls internal onTick, then listener's onTick
	 * 2. onFinish: Calls internal onFinish, then listener's onFinish, resets state,
	 *    and clears the timer reference
	 * 
	 * Note: This method does not check if a timer is already running; callers should
	 * cancel existing timers before calling this.
	 */
	private fun createAndStartTimer() {
		countDownTimer = object : CountDownTimer(timeRemaining, countDownInterval) {
			override fun onTick(millisUntilFinished: Long) {
				timeRemaining = millisUntilFinished
				this@SimpleTimerUtils.onTick(millisUntilFinished)
				weakListener?.get()?.onTick(millisUntilFinished)
			}

			override fun onFinish() {
				this@SimpleTimerUtils.onFinish()
				weakListener?.get()?.onFinish()

				timeRemaining = millisInFuture
				isRunning = false
				isPaused = false
				countDownTimer = null
			}
		}
		countDownTimer?.start()
	}

	/**
	 * Starts or resumes the timer from the current timeRemaining value.
	 * 
	 * This method handles three scenarios:
	 * 1. If the timer is already running, does nothing
	 * 2. If the timer was paused, resumes from the saved timeRemaining
	 * 3. If the timer was canceled or finished, starts fresh from millisInFuture
	 * 
	 * The method cancels any existing CountDownTimer before creating a new one to ensure
	 * clean state. It sets isRunning = true and isPaused = false.
	 * 
	 * Thread safety: This method can be called from any thread, but the CountDownTimer
	 * callbacks will always occur on the main/UI thread.
	 * 
	 * Usage example:
	 * ```kotlin
	 * val timer = SimpleTimerUtils(10000, 1000)
	 * timer.start() // Starts 10-second countdown
	 * ```
	 */
	fun start() {
		if (isRunning) return
		countDownTimer?.cancel()

		if (isPaused) isPaused = false
		else timeRemaining = millisInFuture

		isRunning = true
		createAndStartTimer()
	}

	/**
	 * Pauses the timer if it is currently running.
	 * 
	 * This method cancels the underlying CountDownTimer but preserves the current
	 * timeRemaining value, allowing the timer to be resumed later from the same point.
	 * 
	 * State changes:
	 * - Cancels and nullifies countDownTimer
	 * - Sets isPaused = true
	 * - Sets isRunning = false
	 * 
	 * If the timer is not running or is already paused, this method does nothing.
	 * 
	 * Usage example:
	 * ```kotlin
	 * timer.start()
	 * // ... after some time
	 * timer.pause() // Timer stops but remembers position
	 * // ... later
	 * timer.resume() // Continues from where it left off
	 * ```
	 * 
	 * Note: Calling pause() on a finished or canceled timer has no effect.
	 */
	fun pause() {
		if (isRunning && !isPaused) {
			countDownTimer?.cancel()
			countDownTimer = null
			isPaused = true
			isRunning = false
		}
	}

	/**
	 * Resumes a paused timer from where it left off.
	 * 
	 * This method simply calls start() when the timer is in a paused state.
	 * If the timer is not paused (e.g., running, canceled, or finished), this method
	 * does nothing.
	 * 
	 * The resume operation preserves the timeRemaining value that was saved during pause,
	 * ensuring the timer completes its remaining duration accurately.
	 * 
	 * Usage example:
	 * ```kotlin
	 * timer.start()
	 * timer.pause()  // Pause at 5 seconds remaining
	 * timer.resume() // Continues from 5 seconds
	 * ```
	 * 
	 * @see pause
	 * @see start
	 */
	fun resume() {
		if (isPaused) start()
	}

	/**
	 * Cancels the timer and resets all state to initial values.
	 * 
	 * This method stops the timer completely and resets:
	 * - Cancels and nullifies the underlying CountDownTimer
	 * - Sets isRunning = false
	 * - Sets isPaused = false
	 * - Resets timeRemaining to millisInFuture
	 * 
	 * Unlike pause(), cancel() cannot be resumed - the timer must be started fresh.
	 * 
	 * After cancel(), calling start() will begin the timer from the full duration
	 * (millisInFuture), not from the previously saved timeRemaining.
	 * 
	 * Usage example:
	 * ```kotlin
	 * timer.start()
	 * // ... timer is running
	 * timer.cancel() // Timer stops and resets
	 * timer.start()  // Starts over from beginning
	 * ```
	 * 
	 * Note: This method is safe to call even if the timer is not running.
	 */
	fun cancel() {
		countDownTimer?.cancel()
		countDownTimer = null
		isRunning = false
		isPaused = false
		timeRemaining = millisInFuture
	}

	/**
	 * Updates the total duration of the timer while preserving running state.
	 * 
	 * This method allows changing the timer's total duration dynamically, even while
	 * the timer is running or paused. It intelligently handles state transitions:
	 * 
	 * 1. Captures whether the timer was running or paused
	 * 2. Cancels the current timer and resets state
	 * 3. Updates millisInFuture and resets timeRemaining to the new value
	 * 4. If the timer was running or paused before, automatically starts it again
	 * 
	 * This is useful for scenarios where the timer duration needs to change based on
	 * user input or game state without losing the active timer context.
	 * 
	 * Usage example:
	 * ```kotlin
	 * val timer = SimpleTimerUtils(30000, 1000)
	 * timer.start()
	 * // User selects a different duration
	 * timer.updateTime(60000) // Timer now runs for 60 seconds total
	 * ```
	 * 
	 * Important: This method resets the timer position to the new full duration.
	 * It does NOT preserve the elapsed time percentage.
	 * 
	 * @param millisInFuture The new total duration for the timer in milliseconds.
	 *                       Must be positive.
	 */
	fun updateTime(millisInFuture: Long) {
		val wasRunning = isRunning || isPaused
		cancel()

		this.millisInFuture = millisInFuture
		this.timeRemaining = millisInFuture

		if (wasRunning) {
			start()
		}
	}

	/**
	 * Callback method invoked on each timer tick.
	 * 
	 * This method provides an empty default implementation that can be overridden
	 * by subclasses to handle tick events directly without implementing the
	 * TimerListener interface.
	 * 
	 * When overridden, this method is called in addition to the TimerListener.onTick
	 * callback, not instead of it. Both will receive the tick event.
	 * 
	 * The default implementation does nothing.
	 * 
	 * Override example:
	 * ```kotlin
	 * class MyTimer(millis: Long, interval: Long) : SimpleTimerUtils(millis, interval) {
	 *     override fun onTick(millisUntilFinished: Long) {
	 *         // Custom tick handling
	 *         Log.d("Timer", "Time remaining: $millisUntilFinished")
	 *     }
	 * }
	 * ```
	 * 
	 * @param millisUntilFinished The number of milliseconds remaining until the timer finishes.
	 *                            This value decreases from millisInFuture to 0.
	 */
	fun onTick(millisUntilFinished: Long) = Unit

	/**
	 * Callback method invoked when the timer finishes.
	 * 
	 * This method provides an empty default implementation that can be overridden
	 * by subclasses to handle completion events directly without implementing the
	 * TimerListener interface.
	 * 
	 * When overridden, this method is called in addition to the TimerListener.onFinish
	 * callback, not instead of it. Both will receive the completion event.
	 * 
	 * The default implementation does nothing.
	 * 
	 * Override example:
	 * ```kotlin
	 * class MyTimer(millis: Long, interval: Long) : SimpleTimerUtils(millis, interval) {
	 *     override fun onFinish() {
	 *         // Custom completion handling
	 *         showToast("Timer completed!")
	 *     }
	 * }
	 * ```
	 * 
	 * Note: When the timer finishes, the internal state is automatically reset:
	 * - timeRemaining is set to millisInFuture
	 * - isRunning and isPaused are set to false
	 * - countDownTimer is nullified
	 */
	fun onFinish() = Unit

	/**
	 * Sets or clears the timer event listener.
	 * 
	 * The listener is stored as a WeakReference to prevent memory leaks. This is
	 * particularly important when the listener is an Activity or Fragment that may
	 * be destroyed while the timer is still referenced elsewhere.
	 * 
	 * Setting a new listener replaces any previously set listener. Setting a null
	 * listener removes the current listener (if any).
	 * 
	 * Memory management benefits:
	 * - Prevents timer from holding a strong reference to Activity/Fragment
	 * - Allows garbage collection even if timer is not canceled
	 * - Safe for configuration changes if the timer outlives the Activity
	 * 
	 * Usage example:
	 * ```kotlin
	 * class MyActivity : AppCompatActivity() {
	 *     private lateinit var timer: SimpleTimerUtils
	 *     
	 *     override fun onCreate(savedInstanceState: Bundle?) {
	 *         super.onCreate(savedInstanceState)
	 *         timer = SimpleTimerUtils(30000, 1000)
	 *         timer.setTimerListener(object : SimpleTimerUtils.TimerListener {
	 *             override fun onTick(millisUntilFinished: Long) {
	 *                 updateCountdownDisplay(millisUntilFinished)
	 *             }
	 *             override fun onFinish() {
	 *                 onTimerComplete()
	 *             }
	 *         })
	 *     }
	 * }
	 * ```
	 * 
	 * @param timerListener The listener to receive timer events, or null to remove
	 *                      the current listener. The listener is stored in a WeakReference.
	 */
	fun setTimerListener(timerListener: TimerListener?) {
		this.weakListener = if (timerListener != null) {
			WeakReference(timerListener)
		} else {
			null
		}
	}

	/**
	 * Interface for receiving timer events.
	 * 
	 * Implement this interface to receive callbacks when the timer ticks or finishes.
	 * The listener is stored via WeakReference, so implementers don't need to worry
	 * about explicit cleanup in most cases.
	 * 
	 * Both callback methods are invoked on the main/UI thread, making it safe to
	 * update UI components directly within the implementations.
	 * 
	 * Example implementation:
	 * ```kotlin
	 * val listener = object : SimpleTimerUtils.TimerListener {
	 *     override fun onTick(millisUntilFinished: Long) {
	 *         val seconds = millisUntilFinished / 1000
	 *         textView.text = "Time left: $seconds seconds"
	 *     }
	 *     
	 *     override fun onFinish() {
	 *         textView.text = "Time's up!"
	 *         button.isEnabled = true
	 *     }
	 * }
	 * ```
	 */
	interface TimerListener {
		/**
		 * Called periodically as the timer counts down.
		 * 
		 * This method is called at intervals specified by the countDownInterval parameter
		 * passed to the SimpleTimerUtils constructor.
		 * 
		 * @param millisUntilFinished The number of milliseconds remaining until the
		 *                            timer finishes. This value will be > 0 for tick
		 *                            callbacks, and decreases over time.
		 */
		fun onTick(millisUntilFinished: Long)

		/**
		 * Called when the timer reaches zero.
		 * 
		 * This method is called exactly once when the countdown completes. After this
		 * callback, the timer is reset to its initial state (isRunning = false,
		 * isPaused = false, timeRemaining = millisInFuture).
		 * 
		 * The timer can be started again after finishing by calling start().
		 */
		fun onFinish()
	}
}