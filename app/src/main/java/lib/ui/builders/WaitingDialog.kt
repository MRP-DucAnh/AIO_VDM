package lib.ui.builders

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import app.core.AIOApp
import app.core.bases.interfaces.BaseActivityInf
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.process.LogHelperUtils
import lib.ui.ViewUtility
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder.OnCancelListener

/**
 * A custom dialog for displaying a loading or waiting indicator with optional
 * cancel handling.
 *
 * This dialog uses a Lottie animation and a message to inform users about an
 * ongoing operation. It optionally shows an OK button and supports
 * cancellability with a custom cancel listener. The dialog is built lazily
 * using [DialogBuilder] and can be shown or closed programmatically.
 *
 * The dialog displays:
 * - A Lottie animation (preloaded from [AIOApp.aioRawFiles] if available)
 * - A customizable loading message
 * - An optional OK button that can be hidden
 *
 * @property baseActivityInf Interface to the base activity, required to
 *     build and manage the dialog lifecycle
 * @property loadingMessage The message displayed to inform the user about
 *     the operation in progress
 * @property shouldHideOkayButton If true, the OK button is hidden from
 *     the dialog
 * @property isCancelable Whether the dialog can be canceled by the user
 * @property dialogCancelListener Optional listener triggered when the dialog
 *     is canceled
 */
class WaitingDialog(
	private val baseActivityInf: BaseActivityInf?,
	private val loadingMessage: String,
	private val shouldHideOkayButton: Boolean = false,
	private val isCancelable: Boolean = true,
	private val dialogCancelListener: OnCancelListener? = null
) {

	/**
	 * Logger instance for debugging and error reporting during dialog
	 * operations.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Lazily initializes the [DialogBuilder] instance and configures its
	 * components.
	 *
	 * The dialog is only created when first accessed, ensuring that resources
	 * are not allocated until the dialog is actually needed. Upon
	 * initialization, it sets up the view, cancel behavior, and content
	 * configuration.
	 *
	 * @return The configured [DialogBuilder] instance, or null if
	 *     [baseActivityInf] is null
	 */
	val dialogBuilder: DialogBuilder? by lazy {
		DialogBuilder(baseActivityInf).apply { initializeDialogComponents() }
	}

	/**
     * Initializes the dialog's layout and core components.
     *
     * This function is called as an extension on [DialogBuilder] when the
     * `dialogBuilder` property is first accessed. It orchestrates the setup by:
     * 1. Setting the custom layout (`R.layout.dialog_waiting_progress_1`).
     * 2. Applying the `isCancelable` property to the dialog.
     * 3. Attaching cancel listeners via [configureCancelListener].
     * 4. Populating the view with content (text, animation) via [configureDialogContent].
     */
	private fun DialogBuilder.initializeDialogComponents() {
		setView(R.layout.dialog_waiting_progress_1)
		setCancelable(isCancelable)
		configureCancelListener()
		configureDialogContent()
	}

	/**
     * Configures cancel listeners to ensure consistent behavior for both dismiss and cancel events.
     *
     * This method sets both `setOnDismissListener` and `setOnCancelListener` to
     * handle all scenarios where the dialog might be closed by the user (e.g.,
     * back press, touching outside the dialog).
     *
     * If a custom [dialogCancelListener] is provided, its `onCancel` method is
     * invoked. If not, the dialog's own `cancel()` method is called to ensure
     * proper cleanup, though this is often redundant as the dismiss/cancel event
     * is already in progress.
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
	 * Configures the text, buttons, and animation for the waiting dialog.
	 *
	 * This method sets up all the visual components of the dialog:
	 * - Sets the loading message text with a fade animation
	 * - Configures the OK button visibility and click listener
	 * - Loads and plays the Lottie animation (using preloaded composition
	 *   if available, otherwise loading from raw resources)
	 */
	private fun DialogBuilder.configureDialogContent() {
		view.apply {
			// Set loading message
			findViewById<TextView>(R.id.txt_progress_info).let {
				it.text = loadingMessage
				ViewUtility.animateFadInOutAnim(it)
			}

			// Configure OK button visibility and click listener
			findViewById<View>(R.id.btn_dialog_positive_container).apply {
				setOnClickListener { close() }
				visibility = if (shouldHideOkayButton) GONE else VISIBLE
			}

			// Setup animation
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
	 * Displays the dialog if it's not already shown.
	 *
	 * This method checks if the dialog is already visible before attempting
	 * to show it, preventing duplicate dialogs. If the dialog builder is not
	 * available (e.g., due to invalid context), an exception is thrown.
	 *
	 * @throws IllegalStateException if the dialog context is unavailable
	 */
	fun show() {
		dialogBuilder?.let { dialogBuilder ->
			if (!dialogBuilder.isShowing) {
				dialogBuilder.show()
			}
		} ?: run {
			logger.e("Cannot show dialog - invalid context")
			throw IllegalStateException("Dialog context unavailable")
		}
	}

	/**
	 * Closes the dialog if it is currently showing.
	 *
	 * This method safely closes the dialog only if it is currently visible.
	 * It returns a boolean indicating whether the dialog was actually closed,
	 * which can be useful for tracking dialog state.
	 *
	 * @return `true` if the dialog was showing and was closed, `false`
	 *     otherwise
	 */
	fun close(): Boolean {
		return dialogBuilder?.let {
			if (it.isShowing) {
				it.close()
				true
			} else false
		} ?: false
	}
}