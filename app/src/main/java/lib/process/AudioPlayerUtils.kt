package lib.process

import android.content.*
import android.media.*
import java.lang.ref.*

open class AudioPlayerUtils(context: Context?) {

	private val logger = LogHelperUtils.from(javaClass)
	private val weakReferenceOfContext = WeakReference(context)

	protected var completionListenerRef: WeakReference<(() -> Unit)>? = null
	protected var errorListenerRef: WeakReference<((Int, Int) -> Unit)>? = null

	protected var mediaPlayer: MediaPlayer? = null

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

	suspend fun startPlaying() {
		withIOContext {
			mediaPlayer?.start()
		}
	}

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

	suspend fun pause() {
		withIOContext {
			mediaPlayer?.takeIf { it.isPlaying }?.pause()
		}
	}

	suspend fun resume() {
		withIOContext {
			mediaPlayer?.takeIf { !it.isPlaying }?.start()
		}
	}

	suspend fun seekTo(positionMs: Int) {
		withIOContext {
			mediaPlayer?.seekTo(positionMs)
		}
	}

	suspend fun setVolume(leftVolume: Float, rightVolume: Float) {
		withIOContext {
			mediaPlayer?.setVolume(leftVolume, rightVolume)
		}
	}

	suspend fun getCurrentPosition(): Int {
		return withIOContext { mediaPlayer?.currentPosition ?: 0 }
	}

	suspend fun getDuration(): Int {
		return withIOContext { mediaPlayer?.duration ?: 0 }
	}

	suspend fun stop() {
		withIOContext {
			mediaPlayer?.apply {
				stop()
				release()
			}
			mediaPlayer = null
		}
	}

	suspend fun isPlaying(): Boolean {
		return withIOContext { mediaPlayer?.isPlaying == true }
	}

	suspend fun setOnCompletionListener(listener: () -> Unit) {
		withIOContext { completionListenerRef = WeakReference(listener) }
	}

	suspend fun setOnErrorListener(listener: (Int, Int) -> Unit) {
		withIOContext { errorListenerRef = WeakReference(listener) }
	}
}