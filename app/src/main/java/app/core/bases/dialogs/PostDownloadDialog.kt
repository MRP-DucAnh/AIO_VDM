package app.core.bases.dialogs

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.core.engines.downloader.DownloadNotification.Companion.FROM_DOWNLOAD_NOTIFICATION
import app.core.engines.downloader.DownloadNotification.Companion.INTENT_EXTRA_SOURCE_ORIGIN
import app.ui.main.MotherActivity
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.process.AudioPlayerUtils
import lib.process.CommonTimeUtils
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.setBounceClick
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import java.lang.ref.WeakReference

/**
 * Confirmation dialog that appears after successfully initiating a download operation.
 *
 * Provides multimodal feedback to users through visual animations and auditory cues,
 * confirming that their download request has been accepted and queued for processing.
 * The dialog serves as both a success notification and a gateway to monitor download
 * progress, offering direct navigation to the downloads management section.
 *
 * Key characteristics:
 * - Non-blocking success notification with animated confirmation
 * - Integrated sound effects synchronized with visual feedback
 * - Memory-safe implementation using WeakReference patterns
 * - Adaptive navigation based on current activity context
 * - Configurable dismissal callbacks for parent coordination
 *
 * @property baseActivity Context provider for dialog display and resource access.
 *                        Must be a valid BaseActivity instance during dialog lifetime.
 * @property onCloseDialog Optional lambda executed upon dialog closure, useful for
 *                        cleanup operations or triggering subsequent UI updates.
 */
class PostDownloadDialog(
	private val baseActivity: BaseActivity,
	private val onCloseDialog: () -> Unit = {}
) {

	/**
	 * Structured logging facility for monitoring dialog lifecycle and debugging.
	 *
	 * Captures operational events including initialization errors, animation states,
	 * audio playback issues, and user interaction patterns to assist troubleshooting
	 * and performance optimization.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Memory-safe reference wrapper for the hosting activity context.
	 *
	 * Prevents strong reference cycles that could prevent activity garbage collection
	 * while allowing safe access to context-dependent resources during the dialog's
	 * active display period.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Null-safe activity accessor with automatic garbage collection awareness.
	 *
	 * Returns the current activity instance if available, otherwise null when the
	 * activity has been destroyed. This prevents crashes from attempting operations
	 * on invalid contexts while maintaining clean failure semantics.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()

	/**
	 * UI construction manager for dialog creation and presentation.
	 *
	 * Handles view inflation, window management, and dialog lifecycle events while
	 * maintaining compatibility with the application's theme and navigation systems.
	 */
	private val dialogBuilder = DialogBuilder(safeBaseActivity)

	/**
	 * Audio management component for feedback sound effects.
	 *
	 * Responsible for loading, preparing, and playing confirmation sounds that
	 * reinforce successful download initiation. Manages audio resource lifecycle
	 * to prevent memory leaks and ensure clean playback termination.
	 */
	private val audioPlayerUtils = AudioPlayerUtils(safeBaseActivity)

	/**
	 * Primary initialization block that configures dialog components.
	 *
	 * Executes setup sequence: prepares audio resources, inflates dialog layout,
	 * and configures visual components including success animation and interface
	 * elements. This setup occurs once during object construction.
	 */
	init {
		audioPlayerUtils.prepare(R.raw.sound_download_added)
		dialogBuilder.setView(R.layout.dialog_waiting_progress_1)
		dialogBuilder.view.apply {
			setupLoadingAnimation()
			configureSuccessLayout()
		}
	}

	/**
	 * Presents the configured dialog to the user interface.
	 *
	 * Activates the dialog window, attaches dismissal handlers, and initiates
	 * synchronized audio-visual feedback. This method should be called after
	 * successful download queueing to provide immediate user confirmation.
	 */
	fun show() {
		dialogBuilder.show()
		configureDismissHandler(onCloseDialog)
		playDownloadAddedSFX()
	}

	/**
	 * Terminates dialog display and initiates cleanup procedures.
	 *
	 * Closes the dialog window, stops any active animations, and triggers
	 * the configured dismissal callbacks. Can be invoked programmatically
	 * or through user interaction with navigation controls.
	 */
	fun close() {
		dialogBuilder.close()
	}

	/**
	 * Configures event listeners for dialog termination scenarios.
	 *
	 * Establishes handlers for both deliberate dismissal and cancellation events,
	 * ensuring consistent resource cleanup and callback execution regardless of
	 * closure mechanism. Manages audio playback termination and parent notification.
	 *
	 * @param onCloseDialog Closure function to execute upon dialog termination,
	 *                     typically used to update parent component state or
	 *                     initiate subsequent workflow steps.
	 */
	private fun configureDismissHandler(onCloseDialog: () -> Unit) {
		dialogBuilder.dialog.setOnDismissListener {
			audioPlayerUtils.stop()
			onCloseDialog.invoke()
		}
		dialogBuilder.dialog.setOnCancelListener {
			audioPlayerUtils.stop()
			onCloseDialog.invoke()
		}
	}

	/**
	 * Initializes and configures the success animation visualization.
	 *
	 * Sets up Lottie animation properties and attempts to load optimized
	 * cached compositions before falling back to raw resource animations.
	 * Implements progressive display with smooth fade-in transitions to
	 * enhance visual feedback quality.
	 *
	 * @receiver Dialog root view providing access to animation component.
	 *           Uses extension method pattern to organize animation-specific
	 *           configuration logic.
	 */
	private fun View.setupLoadingAnimation() {
		findViewById<LottieAnimationView>(R.id.img_progress_circle)?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			val loadedComposition = aioRawFiles.getSuccessfulDownloadComposition()
			if (loadedComposition != null) {
				setComposition(loadedComposition)
				playAnimation()
				showView(this, true, 100)
			} else {
				aioRawFiles.getSuccessfulDownloadComposition()
				setAnimation(R.raw.animation_successful)
				showView(this, true, 100)
			}
		}
	}

	/**
	 * Configures dialog interface elements and interaction handlers.
	 *
	 * Sets up button labeling, navigation logic, and success messaging
	 * according to current application state. Implements context-aware
	 * navigation that adapts based on whether the user is already within
	 * the main application activity or requires activity transition.
	 *
	 * @receiver Dialog root view containing interface components to configure.
	 *           Uses early exit pattern when activity context becomes unavailable.
	 */
	private fun View.configureSuccessLayout() {
		if (safeBaseActivity == null) return

		findViewById<TextView>(R.id.btn_dialog_positive).apply {
			text = getText(R.string.title_view_downloads)
			setLeftSideDrawable(R.drawable.ic_button_arrow_next)
		}

		findViewById<View>(R.id.btn_dialog_positive_container)
			?.setBounceClick { _, _ ->
				close()
				if (safeBaseActivity is MotherActivity) {
					(safeBaseActivity as? MotherActivity)?.openDownloadsFragment()
				} else {
					val openingIntent = Intent(safeBaseActivity, MotherActivity::class.java)
					openingIntent.putExtra(INTENT_EXTRA_SOURCE_ORIGIN, FROM_DOWNLOAD_NOTIFICATION)
					safeBaseActivity?.startActivity(openingIntent)
				}
				onCloseDialog.invoke()
			}

		findViewById<TextView>(R.id.txt_progress_info)
			?.apply { text = getText(R.string.title_download_added_successfully) }
	}

	/**
	 * Plays auditory confirmation synchronized with visual feedback.
	 *
	 * Calculates optimal playback timing based on animation loading state,
	 * delaying sound effect when animations require additional loading time
	 * to ensure synchronized multimodal feedback. Includes error handling
	 * for audio subsystem failures.
	 *
	 * @throws Exception Propagates audio playback or timing-related exceptions
	 *                  to calling context with appropriate logging.
	 */
	private fun playDownloadAddedSFX() {
		try {
			val lottieFile = aioRawFiles.getSuccessfulDownloadComposition()
			val timeInMile = if (lottieFile == null) 1500 else 100
			CommonTimeUtils.delay(timeInMile, object : OnTaskFinishListener {
				override fun afterDelay() {
					audioPlayerUtils.startPlaying()
				}
			})
		} catch (error: Exception) {
			logger.e("Error in playing sfx audio", error)
		}
	}
}