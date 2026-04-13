package lib.process

import android.content.*
import android.media.*
import java.lang.ref.*

/**
 * A utility class for playing audio resources with coroutine support.
 * This class provides a comprehensive set of methods for audio playback operations
 * including preparing, playing, pausing, resuming, seeking, volume control, and
 * error handling. All operations are performed on IO threads to avoid blocking
 * the UI thread.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Coroutine Support:</b> All playback methods are suspend functions that
 *       execute on IO threads using withIOContext</li>
 *   <li><b>Memory Leak Prevention:</b> Uses WeakReference for Context and listeners
 *       to prevent memory leaks when Activities/Fragments are destroyed</li>
 *   <li><b>Automatic Resource Management:</b> Properly releases MediaPlayer resources
 *       when playback completes or errors occur</li>
 *   <li><b>Flexible Playback Options:</b> Separate prepare/start and combined play
 *       methods for different use cases</li>
 *   <li><b>Event Callbacks:</b> Completion and error listeners with automatic
 *       resource cleanup</li>
 * </ul>
 *
 * <p><b>Typical Usage Examples:</b></p>
 * <pre>
 * // Example 1: Simple playback
 * val audioPlayer = AudioPlayerUtils(context)
 * lifecycleScope.launch {
 *     audioPlayer.play(R.raw.sound_effect)
 * }
 *
 * // Example 2: Controlled playback with callbacks
 * val audioPlayer = AudioPlayerUtils(context)
 * lifecycleScope.launch {
 *     audioPlayer.setOnCompletionListener {
 *         Log.d("Audio", "Playback completed")
 *         updateUIForPlaybackEnd()
 *     }
 *     audioPlayer.setOnErrorListener { what, extra ->
 *         Log.e("Audio", "Error occurred: what=$what, extra=$extra")
 *         showErrorMessage()
 *     }
 *     audioPlayer.prepare(R.raw.music_track)
 *     audioPlayer.startPlaying()
 *     // Later...
 *     audioPlayer.pause()
 *     audioPlayer.resume()
 * }
 * </pre>
 *
 * <p><b>Thread Safety:</b></p>
 * All methods are thread-safe as they execute on IO threads via withIOContext.
 * The class uses coroutines to ensure proper threading behavior without blocking
 * the main thread.
 *
 * <p><b>Lifecycle Management:</b></p>
 * The class uses WeakReference for Context to avoid leaking Activities. However,
 * it's recommended to call stop() in lifecycle callbacks (e.g., onStop, onDestroy)
 * to release resources promptly. The MediaPlayer automatically cleans up when
 * playback completes or errors occur.
 *
 * @param context The Android Context needed for accessing audio resources.
 *                Stored in a WeakReference to prevent memory leaks.
 * @author Generated
 * @since 1.0
 */
open class AudioPlayerUtils(context: Context?) {

	/**
	 * Logger instance for debugging, error tracking, and performance monitoring.
	 * Automatically configured with the class name for easy log filtering.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * WeakReference to the Android Context to prevent memory leaks.
	 * This allows the Activity/Service to be garbage collected even if the
	 * AudioPlayerUtils instance is still referenced.
	 */
	private val weakReferenceOfContext = WeakReference(context)

	/**
	 * WeakReference to the completion listener callback.
	 * Using WeakReference prevents memory leaks if the parent component is destroyed
	 * before playback completes. The listener is invoked when media playback finishes
	 * naturally or is stopped.
	 */
	protected var completionListenerRef: WeakReference<(() -> Unit)>? = null

	/**
	 * WeakReference to the error listener callback.
	 * Using WeakReference prevents memory leaks if the parent component is destroyed
	 * before an error occurs. The listener receives error codes to help identify
	 * and handle playback failures.
	 */
	protected var errorListenerRef: WeakReference<((Int, Int) -> Unit)>? = null

	/**
	 * The underlying MediaPlayer instance used for audio playback.
	 * This is recreated each time prepare() or play() is called to ensure a clean state.
	 * Null when no media is loaded or after stop() is called.
	 */
	protected var mediaPlayer: MediaPlayer? = null

	/**
	 * Prepares an audio resource for playback without starting it immediately.
	 * This method loads and prepares the media but does not begin playback, allowing
	 * you to configure additional settings before calling startPlaying().
	 *
	 * <p>This method automatically stops any currently playing audio and releases
	 * existing MediaPlayer resources before preparing the new resource.</p>
	 *
	 * @param resId The resource ID of the audio file (e.g., R.raw.sound_effect)
	 * @see #startPlaying()
	 * @see #play(Int)
	 * @see #stop()
	 */
	suspend fun prepare(resId: Int) {
		withIOContext {
			stop()
			weakReferenceOfContext.get()?.let { contextRef ->
				mediaPlayer = MediaPlayer.create(contextRef, resId)?.apply {
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
	}

	/**
	 * Starts playback of the currently prepared audio.
	 * This method should be called after prepare() to begin audio playback.
	 * If no media is prepared or the media is already playing, this method does nothing.
	 *
	 * @see #prepare(Int)
	 * @see #pause()
	 * @see #resume()
	 */
	suspend fun startPlaying() {
		withIOContext {
			mediaPlayer?.start()
		}
	}

	/**
	 * Plays an audio resource directly (prepare and play in one step).
	 * This convenience method combines prepare and start into a single operation.
	 * Any currently playing audio is stopped and released before playing the new resource.
	 *
	 * <p><b>Note:</b> This method is ideal for sound effects or short clips where
	 * you don't need separate preparation and playback control. For longer media
	 * where you need pause/resume capabilities, consider using prepare() followed
	 * by startPlaying().</p>
	 *
	 * @param resId The resource ID of the audio file to play
	 * @see #prepare(Int)
	 * @see #startPlaying()
	 * @see #stop()
	 */
	suspend fun play(resId: Int) {
		withIOContext {
			stop()
			weakReferenceOfContext.get()?.let { contextRef ->
				mediaPlayer = MediaPlayer.create(contextRef, resId)?.apply {
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
	}

	/**
	 * Pauses the currently playing audio.
	 * The playback position is preserved and can be resumed later with resume().
	 * If no audio is playing or the media is already paused, this method does nothing.
	 *
	 * @see #resume()
	 * @see #startPlaying()
	 * @see #isPlaying()
	 */
	suspend fun pause() {
		withIOContext {
			mediaPlayer?.takeIf { it.isPlaying }?.pause()
		}
	}

	/**
	 * Resumes playback from the paused position.
	 * This method only works if media has been previously paused. For stopped or
	 * uninitialized media, use prepare() and startPlaying() instead.
	 *
	 * @see #pause()
	 * @see #startPlaying()
	 * @see #isPlaying()
	 */
	suspend fun resume() {
		withIOContext {
			mediaPlayer?.takeIf { !it.isPlaying }?.start()
		}
	}

	/**
	 * Seeks to a specific position in the audio stream.
	 * This allows jumping to any point within the audio duration.
	 *
	 * @param positionMs The position to seek to, in milliseconds. Valid values range
	 *                   from 0 (beginning) to the total duration (end of audio).
	 * @see #getCurrentPosition()
	 * @see #getDuration()
	 */
	suspend fun seekTo(positionMs: Int) {
		withIOContext {
			mediaPlayer?.seekTo(positionMs)
		}
	}

	/**
	 * Sets the volume level for left and right audio channels independently.
	 * Useful for implementing mute, volume control, balance, or audio fading effects.
	 *
	 * @param leftVolume Volume level for the left channel. Range: 0.0 (silent) to
	 *                   1.0 (maximum volume)
	 * @param rightVolume Volume level for the right channel. Range: 0.0 (silent) to
	 *                    1.0 (maximum volume)
	 */
	suspend fun setVolume(leftVolume: Float, rightVolume: Float) {
		withIOContext {
			mediaPlayer?.setVolume(leftVolume, rightVolume)
		}
	}

	/**
	 * Gets the current playback position.
	 *
	 * @return Current position in milliseconds, or 0 if no media is loaded or
	 *         the media player is not initialized
	 * @see #seekTo(Int)
	 * @see #getDuration()
	 */
	suspend fun getCurrentPosition(): Int {
		return withIOContext { mediaPlayer?.currentPosition ?: 0 }
	}

	/**
	 * Gets the total duration of the loaded audio.
	 *
	 * @return Total duration in milliseconds, or 0 if no media is loaded or
	 *         the duration cannot be determined
	 * @see #getCurrentPosition()
	 * @see #seekTo(Int)
	 */
	suspend fun getDuration(): Int {
		return withIOContext { mediaPlayer?.duration ?: 0 }
	}

	/**
	 * Stops playback and releases all MediaPlayer resources.
	 * After calling this method, the MediaPlayer is set to null and cannot be
	 * resumed. To play audio again, you must call prepare() or play().
	 *
	 * <p><b>Important:</b> Always call this method when audio playback is no longer
	 * needed to free system resources. This is especially important in lifecycle
	 * methods like onStop() or onDestroy().</p>
	 *
	 * @see #pause()
	 * @see #play(Int)
	 * @see #prepare(Int)
	 */
	suspend fun stop() {
		withIOContext {
			mediaPlayer?.apply {
				stop()
				release()
			}
			mediaPlayer = null
		}
	}

	/**
	 * Checks whether audio is currently playing.
	 *
	 * @return true if audio is playing, false if paused, stopped, or no media is loaded
	 * @see #pause()
	 * @see #resume()
	 * @see #startPlaying()
	 */
	suspend fun isPlaying(): Boolean {
		return withIOContext { mediaPlayer?.isPlaying == true }
	}

	/**
	 * Sets a listener to be notified when audio playback completes.
	 * The listener is stored in a WeakReference to prevent memory leaks.
	 *
	 * <p>The completion listener is automatically invoked when:
	 * <ul>
	 *   <li>Playback reaches the end of the audio file naturally</li>
	 *   <li>stop() is called (completion listener fires before cleanup)</li>
	 * </ul>
	 * </p>
	 *
	 * @param listener Callback function invoked on playback completion
	 * @see #setOnErrorListener(OnErrorListener)
	 */
	suspend fun setOnCompletionListener(listener: () -> Unit) {
		withIOContext { completionListenerRef = WeakReference(listener) }
	}

	/**
	 * Sets a listener to be notified when errors occur during audio playback.
	 * The listener is stored in a WeakReference to prevent memory leaks.
	 *
	 * <p>Common error scenarios include:
	 * <ul>
	 *   <li>Invalid or corrupted audio resource</li>
	 *   <li>Unsupported audio format</li>
	 *   <li>Insufficient memory or system resources</li>
	 *   <li>Hardware decoder failures</li>
	 * </ul>
	 * </p>
	 *
	 * @param listener Callback function receiving error codes (what, extra)
	 * @see #setOnCompletionListener(OnCompletionListener)
	 */
	suspend fun setOnErrorListener(listener: (Int, Int) -> Unit) {
		withIOContext { errorListenerRef = WeakReference(listener) }
	}
}