package app.core.engines.caches

import androidx.annotation.RawRes
import app.core.AIOApp
import com.aio.R
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory.fromRawRes
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieFeatureFlag
import kotlinx.coroutines.*
import lib.process.LogHelperUtils

class AIORawFiles {

	private val logger = LogHelperUtils.from(javaClass)

	private var layoutLoadComposition: LottieComposition? = null
	private var waitingLoadingComposition: LottieComposition? = null
	private var noResultEmptyComposition: LottieComposition? = null
	private var openActiveTasksComposition: LottieComposition? = null
	private var downloadReadyComposition: LottieComposition? = null
	private var successfulDownloadComposition: LottieComposition? = null
	private var circleLoadingComposition: LottieComposition? = null
	private var audioVisualizingV1Composition: LottieComposition? = null
	private var audioVisualizingComposition: LottieComposition? = null
	private var emptyGhostComposition: LottieComposition? = null
	private var newVersionUpdateComposition: LottieComposition? = null
	private var loginRequiredComposition: LottieComposition? = null

	init {
		// Enable global Lottie feature
		LottieDrawable().enableFeatureFlag(LottieFeatureFlag.MergePathsApi19, true)
	}

	// Lazy load helper
	private fun lazyLoad(@RawRes resId: Int, setter: (LottieComposition) -> Unit) {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val comp = loadAnimation(resId)
				setter(comp)
				logger.d("Lazy loaded animation $resId")
			} catch (e: Exception) {
				logger.w("Failed to lazy load animation $resId: ${e.message}")
			}
		}
	}

	@OptIn(InternalCoroutinesApi::class)
	private suspend fun loadAnimation(@RawRes res: Int): LottieComposition =
		suspendCancellableCoroutine { cont ->
			fromRawRes(AIOApp.INSTANCE, res)
				.addListener { comp ->
					cont.tryResume(comp)?.let { cont.completeResume(it) }
				}
				.addFailureListener { ex ->
					cont.tryResumeWithException(ex)?.let { cont.completeResume(it) }
				}
		}

	// Lazy getters
	fun getLayoutLoadComposition(): LottieComposition? {
		if (layoutLoadComposition == null)
			lazyLoad(R.raw.animation_layout_load) { layoutLoadComposition = it }
		return layoutLoadComposition
	}

	fun getWaitingLoadingComposition(): LottieComposition? {
		if (waitingLoadingComposition == null)
			lazyLoad(R.raw.animation_waiting_loading) { waitingLoadingComposition = it }
		return waitingLoadingComposition
	}

	fun getNoResultEmptyComposition(): LottieComposition? {
		if (noResultEmptyComposition == null)
			lazyLoad(R.raw.animation_no_result) { noResultEmptyComposition = it }
		return noResultEmptyComposition
	}

	fun getOpenActiveTasksAnimationComposition(): LottieComposition? {
		if (openActiveTasksComposition == null)
			lazyLoad(R.raw.animation_active_tasks) { openActiveTasksComposition = it }
		return openActiveTasksComposition
	}

	fun getDownloadFoundAnimationComposition(): LottieComposition? {
		if (downloadReadyComposition == null)
			lazyLoad(R.raw.animation_videos_found) { downloadReadyComposition = it }
		return downloadReadyComposition
	}

	fun getSuccessfulDownloadComposition(): LottieComposition? {
		if (successfulDownloadComposition == null)
			lazyLoad(R.raw.animation_successful) { successfulDownloadComposition = it }
		return successfulDownloadComposition
	}

	fun getCircleLoadingComposition(): LottieComposition? {
		if (circleLoadingComposition == null)
			lazyLoad(R.raw.animation_circle_loading) { circleLoadingComposition = it }
		return circleLoadingComposition
	}

	fun getAudioVisualizingV1Composition(): LottieComposition? {
		if (audioVisualizingV1Composition == null)
			lazyLoad(R.raw.animation_audio_visualizing_v1) { audioVisualizingV1Composition = it }
		return audioVisualizingV1Composition
	}

	fun getAudioVisualizingComposition(): LottieComposition? {
		if (audioVisualizingComposition == null)
			lazyLoad(R.raw.animation_audio_visualizing) { audioVisualizingComposition = it }
		return audioVisualizingComposition
	}

	fun getEmptyGhostComposition(): LottieComposition? {
		if (emptyGhostComposition == null)
			lazyLoad(R.raw.animation_empty_ghost) { emptyGhostComposition = it }
		return emptyGhostComposition
	}

	fun getNewVersionUpdateComposition(): LottieComposition? {
		if (newVersionUpdateComposition == null)
			lazyLoad(R.raw.animation_new_app_version) { newVersionUpdateComposition = it }
		return newVersionUpdateComposition
	}

	fun getLoginRequiredComposition(): LottieComposition? {
		if (loginRequiredComposition == null)
			lazyLoad(R.raw.animation_login_required) { loginRequiredComposition = it }
		return loginRequiredComposition
	}
}