package lib.ui.builders

import android.view.*
import android.view.View.*
import android.widget.*
import androidx.lifecycle.*
import app.core.*
import app.core.bases.interfaces.*
import com.aio.R
import com.airbnb.lottie.*
import kotlinx.coroutines.*
import lib.process.*
import lib.ui.*
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder.*

/**
 * A customizable waiting dialog that displays a loading animation and optional message.
 * This dialog is designed to be used with activities that implement BaseActivityInf and
 * provides coroutine support for showing and dismissing the dialog asynchronously.
 *
 * The dialog features:
 * - A Lottie animation for loading indication
 * - Customizable loading message with fade animation
 * - Optional "Okay" button that can be hidden
 * - Cancelable behavior with optional cancel listener
 * - Coroutine-safe show/close operations on the main thread
 *
 * @property baseActivityInf The activity interface providing context and lifecycle scope
 * @property loadingMessage The text message to display below the loading animation
 * @property shouldHideOkayButton Whether to hide the confirmation button
 * @property isCancelable Whether the dialog can be dismissed by tapping outside or back button
 * @property dialogCancelListener Optional listener for dialog cancellation events
 *
 * @author Generated
 * @since 1.0
 */
class WaitingDialog(
	private val baseActivityInf: BaseActivityInf?,
	private val loadingMessage: String,
	private val shouldHideOkayButton: Boolean = false,
	private val isCancelable: Boolean = true,
	private val dialogCancelListener: OnCancelListener? = null
) {

	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Lazy-initialized DialogBuilder instance that manages the underlying dialog.
	 * Returns null if the baseActivityInf is not available.
	 */
	val dialogBuilder: DialogBuilder? by lazy {
		DialogBuilder(baseActivityInf)
	}

	/**
	 * Initializes the dialog components asynchronously when the dialog is created.
	 * Launches a coroutine in the activity's lifecycle scope to set up the dialog UI
	 * and configuration.
	 */
	init {
		dialogBuilder?.apply {
			baseActivityInf?.getActivity()?.lifecycleScope?.launch {
				initializeDialogComponents()
			}
		}
	}

	/**
	 * Initializes the dialog components including layout, cancel behavior, and content.
	 * This function must be called from a coroutine context as it performs suspend operations.
	 *
	 * @receiver DialogBuilder The dialog builder instance being configured
	 */
	private suspend fun DialogBuilder.initializeDialogComponents() {
		setView(R.layout.dialog_waiting_progress_1)
		setCancelable(isCancelable)
		configureCancelListener()
		configureDialogContent()
	}

	/**
	 * Configures dismiss and cancel listeners for the dialog.
	 * When the dialog is dismissed or canceled, it invokes the provided cancel listener
	 * if available, otherwise it cancels the dialog by default.
	 *
	 * @receiver DialogBuilder The dialog builder whose dialog is being configured
	 */
	private fun DialogBuilder.configureCancelListener() {
		dialog.setOnDismissListener { dialog ->
			dialogCancelListener?.onCancel(dialog) ?: dialog?.cancel()
		}

		dialog.setOnCancelListener { dialog ->
			dialogCancelListener?.onCancel(dialog) ?: dialog?.cancel()
		}
	}

	/**
	 * Configures the visual content of the dialog including:
	 * - Loading message text with fade animation
	 * - Optional "Okay" button visibility and click handler
	 * - Lottie loading animation
	 *
	 * This function must be called from a coroutine context as it performs suspend operations.
	 *
	 * @receiver DialogBuilder The dialog builder whose view is being configured
	 */
	private suspend fun DialogBuilder.configureDialogContent() {
		view.apply {
			findViewById<TextView>(R.id.txt_progress_info).let {
				it.text = loadingMessage
				ViewUtility.animateFadInOutAnim(it)
			}

			findViewById<View>(R.id.btn_dialog_positive_container).apply {
				setOnClickListener { close() }
				visibility = if (shouldHideOkayButton) GONE else VISIBLE
			}

			findViewById<LottieAnimationView>(R.id.img_progress_circle).apply {
				AIOApp.aioRawFiles.getWaitingLoadingComposition()?.let {
					setComposition(it)
					playAnimation()
				} ?: run { setAnimation(R.raw.animation_waiting_loading) }

				showView(targetView = this, shouldAnimate = true, animTimeout = 400)
			}
		}
	}

	/**
	 * Displays the waiting dialog on the screen.
	 * This function safely switches to the main thread before showing the dialog.
	 *
	 * @throws IllegalStateException If the dialog builder context is unavailable
	 */
	suspend fun show() {
		withMainContext {
			dialogBuilder?.let { dialogBuilder ->
				if (!dialogBuilder.isShowing) {
					dialogBuilder.show()
				}
			} ?: run {
				logger.e("Cannot show dialog - invalid context")
				throw IllegalStateException("Dialog context unavailable")
			}
		}
	}

	/**
	 * Closes/dismisses the waiting dialog if it is currently showing.
	 * This function safely switches to the main thread before closing the dialog.
	 *
	 * @return true if the dialog was showing and was successfully closed,
	 *         false if the dialog was not showing or the dialog builder is unavailable
	 */
	suspend fun close(): Boolean {
		return withMainContext {
			dialogBuilder?.let {
				if (it.isShowing) {
					it.close()
					true
				} else false
			} ?: false
		}
	}
}