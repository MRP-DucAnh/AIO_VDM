package app.core.engines.caches

import androidx.annotation.*
import app.core.*
import com.aio.R
import com.airbnb.lottie.*
import com.airbnb.lottie.LottieCompositionFactory.*
import kotlinx.coroutines.*
import lib.process.*

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
		val mergePathsApi19 = LottieFeatureFlag.MergePathsApi19
		LottieDrawable().enableFeatureFlag(mergePathsApi19, true)
	}

	private fun lazyLoad(@RawRes resId: Int, setter: (LottieComposition) -> Unit) {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val comp = loadAnimation(resId)
				setter(comp)
			} catch (error: Exception) {
				logger.e(error)
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