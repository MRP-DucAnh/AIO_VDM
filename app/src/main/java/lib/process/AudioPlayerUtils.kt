package lib.process

import android.content.Context
import android.media.MediaPlayer
import java.lang.ref.WeakReference

/**
 * Utility class for simplified audio playback management in Android applications.
 *
 * Provides a clean, memory-safe abstraction over Android's MediaPlayer API with
 * automatic resource management and lifecycle handling. Designed for short audio
 * feedback clips, sound effects, and notification sounds rather than long-form
 * media playback. Implements WeakReference patterns to prevent common memory
 * leaks associated with MediaPlayer and Context references.
 *
 * Key features:
 * - Automatic MediaPlayer lifecycle management
 * - Memory-safe context references using WeakReference
 * - Simplified playback controls with single-method operations
 * - Built-in completion and error callback support
 * - Resource cleanup on stop/destroy cycles
 *
 * Usage patterns:
 * 1. For one-shot sounds: `play(resourceId)`
 * 2. For controlled playback: `prepare(resourceId)` + `startPlaying()`/`pause()`/`stop()`
 * 3. For UI feedback sounds: Use with completion listeners for synchronization
 */
open class AudioPlayerUtils(context: Context?) {

	/**
	 * Logging utility for tracking playback lifecycle and debugging issues.
	 *
	 * Records MediaPlayer state transitions, resource preparation events,
	 * playback errors, and memory management operations. Essential for
	 * diagnosing audio subsystem problems in production environments.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the Android Context for memory-safe resource access.
	 *
	 * Prevents Activity/Context memory leaks by allowing garbage collection
	 * while still providing access to application resources during playback.
	 * Returns null when the referenced Context is no longer available.
	 */
	private val weakReferenceOfContext = WeakReference(context)

	/**
	 * Optional callback for audio playback completion events.
	 *
	 * Wrapped in WeakReference to prevent listener memory leaks. Invoked when
	 * MediaPlayer reaches the end of the audio resource naturally or after
	 * stop() is called. Useful for chaining sounds or updating UI state.
	 */
	protected var completionListenerRef: WeakReference<(() -> Unit)>? = null

	/**
	 * Optional callback for MediaPlayer error conditions.
	 *
	 * Receives Android MediaPlayer error codes (what, extra) when playback
	 * fails. WeakReference prevents callback retention beyond useful lifecycle.
	 * Return value from listener determines error propagation to system.
	 */
	protected var errorListenerRef: WeakReference<((Int, Int) -> Unit)>? = null

	/**
	 * Core MediaPlayer instance managed by this utility.
	 *
	 * Created on demand and released after each playback session. Null when
	 * no audio is prepared or playing. Lifetime follows prepare-play-stop cycle
	 * with automatic cleanup to prevent resource exhaustion.
	 */
	protected var mediaPlayer: MediaPlayer? = null

	/**
	 * Prepares audio resource for deferred playback without immediate start.
	 *
	 * Initializes MediaPlayer with the specified resource ID, sets up completion
	 * and error listeners, and prepares for controlled playback via startPlaying().
	 * Automatically stops any currently playing audio before preparation.
	 *
	 * @param resId Android raw resource ID (R.raw.*) of the audio file to prepare.
	 *              Must be a valid audio resource accessible from the context.
	 */
	fun prepare(resId: Int) {
		stop()

		weakReferenceOfContext.get()?.let { safeContextRef ->
			mediaPlayer = MediaPlayer.create(safeContextRef, resId)?.apply {
				setOnCompletionListener {
					completionListenerRef?.get()?.invoke()
					stop()
				}

				setOnErrorListener { _, what, extra ->
					errorListenerRef?.get()?.invoke(what, extra)
					stop()
					false
				}
			}
		}
	}

	/**
	 * Begins playback of previously prepared audio resource.
	 *
	 * Starts MediaPlayer playback if audio has been prepared via prepare().
	 * No effect if MediaPlayer is null or already playing. For immediate
	 * play-and-forget operation, use play() method instead.
	 */
	fun startPlaying() {
		mediaPlayer?.start()
	}

	/**
	 * Single-method audio playback with automatic resource management.
	 *
	 * Combines preparation and playback in one operation, ideal for short
	 * sound effects. Stops any current playback, creates new MediaPlayer,
	 * starts immediately, and sets up automatic cleanup on completion.
	 *
	 * @param resId Android raw resource ID of audio file to play immediately.
	 */
	fun play(resId: Int) {
		stop()

		weakReferenceOfContext.get()?.let { safeContextRef ->
			mediaPlayer = MediaPlayer.create(safeContextRef, resId)?.apply {
				start()

				setOnCompletionListener {
					completionListenerRef?.get()?.invoke()
					stop()
				}

				setOnErrorListener { _, what, extra ->
					errorListenerRef?.get()?.invoke(what, extra)
					stop()
					false
				}
			}
		}
	}

	/**
	 * Temporarily pauses audio playback while maintaining MediaPlayer state.
	 *
	 * Pauses the MediaPlayer only if it's currently playing. Playback can be
	 * resumed from the same position using resume(). Does not release resources.
	 */
	fun pause() {
		mediaPlayer?.takeIf { it.isPlaying }?.pause()
	}

	/**
	 * Resumes playback from the current paused position.
	 *
	 * Restarts MediaPlayer playback if it was previously paused. No effect if
	 * MediaPlayer is already playing, stopped, or null.
	 */
	fun resume() {
		mediaPlayer?.takeIf { !it.isPlaying }?.start()
	}

	/**
	 * Sets playback position within the current audio resource.
	 *
	 * @param positionMs Desired playback position in milliseconds from start.
	 *                   Clamped automatically by MediaPlayer to valid range.
	 */
	fun seekTo(positionMs: Int) {
		mediaPlayer?.seekTo(positionMs)
	}

	/**
	 * Adjusts stereo channel volume levels independently.
	 *
	 * @param leftVolume Left channel volume (0.0 = silent, 1.0 = full volume).
	 * @param rightVolume Right channel volume (0.0 = silent, 1.0 = full volume).
	 */
	fun setVolume(leftVolume: Float, rightVolume: Float) {
		mediaPlayer?.setVolume(leftVolume, rightVolume)
	}

	/**
	 * Retrieves current playback position within the audio timeline.
	 *
	 * @return Current position in milliseconds, or 0 if no MediaPlayer exists.
	 */
	fun getCurrentPosition(): Int {
		return mediaPlayer?.currentPosition ?: 0
	}

	/**
	 * Gets total duration of the currently loaded audio resource.
	 *
	 * @return Total duration in milliseconds, or 0 if no MediaPlayer exists.
	 */
	fun getDuration(): Int = mediaPlayer?.duration ?: 0

	/**
	 * Stops playback and releases all MediaPlayer resources.
	 *
	 * Performs complete cleanup: stops playback, releases MediaPlayer resources,
	 * and nullifies the reference. Always call this when done with playback to
	 * prevent resource leaks. Automatically called on completion and error events.
	 */
	fun stop() {
		mediaPlayer?.apply {
			stop()
			release()
		}
		mediaPlayer = null
	}

	/**
	 * Checks if audio is currently playing.
	 *
	 * @return True if MediaPlayer exists and is in playing state, false otherwise.
	 */
	fun isPlaying(): Boolean {
		return mediaPlayer?.isPlaying == true
	}

	/**
	 * Registers callback for playback completion events.
	 *
	 * @param listener Lambda invoked when audio playback completes naturally.
	 *                 Wrapped in WeakReference to prevent memory leaks.
	 */
	fun setOnCompletionListener(listener: () -> Unit) {
		completionListenerRef = WeakReference(listener)
	}

	/**
	 * Registers callback for MediaPlayer error events.
	 *
	 * @param listener Lambda receiving error codes (what, extra) when playback fails.
	 *                 Return value ignored; errors always stop playback internally.
	 */
	fun setOnErrorListener(listener: (Int, Int) -> Unit) {
		errorListenerRef = WeakReference(listener)
	}
}