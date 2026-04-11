package app.ui.main.fragments.settings.activities.downloads.dialogs

import android.view.*
import android.widget.*
import app.core.bases.*
import app.core.engines.settings.*
import com.aio.*
import kotlinx.coroutines.*
import lib.process.*
import lib.texts.*
import lib.ui.builders.*
import lib.ui.listeners.*
import java.lang.ref.*

/**
 * Dialog for configuring maximum automatic retry attempts for failed downloads.
 *
 * Provides a user interface with a seekbar slider to set the maximum number of
 * automatic retry attempts when downloads encounter errors. This configuration
 * helps balance between download reliability and resource conservation by
 * controlling how persistently the system attempts to recover from failures.
 *
 * The retry limit can range from 12 to 32 attempts, providing a reasonable
 * spectrum from moderate persistence (12 retries) to high persistence (32 retries).
 * Each retry typically occurs after a delay, with potential exponential backoff
 * depending on the download engine implementation.
 *
 * Features:
 * - Real-time retry count preview as slider moves
 * - Simple numeric display of retry attempts
 * - Immediate persistence to application settings
 * - Memory-safe activity references using WeakReference
 * - Duplicate dialog prevention
 * - Simplified UI with hidden cancel button for focused interaction
 *
 * @property baseActivity BaseActivity context for UI operations (nullable for safety)
 * @property onApplyClick Callback invoked after successful configuration application
 *                       (typically for UI refresh or download manager reconfiguration)
 */
class GlobalMaxAutoRetryLimit(
	baseActivity: BaseActivity?,
	val onApplyClick: () -> Unit = {}
) {

	/**
	 * Logger instance for tracking dialog lifecycle events and user interactions.
	 *
	 * Records initialization, selection changes, validation errors, and apply operations
	 * to assist with debugging and user behavior analysis.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference wrapper for the base activity to prevent memory leaks.
	 *
	 * Safely holds activity reference without preventing garbage collection.
	 * Essential for dialogs that might outlive their parent activities.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Safe accessor for activity reference with null safety.
	 *
	 * Returns the activity if still available, null if garbage collected.
	 * All UI operations should check this property before proceeding.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfBaseActivity.get()

	/**
	 * Direct accessor for global application settings.
	 *
	 * Provides read/write access to user agent configuration through the
	 * injected AIOSettings instance. Acts as a convenience property.
	 */
	private val aioSettings get() = AIOSettingsRepo.getSettings()

	/**
	 * Dialog builder instance configured for retry limit selection.
	 *
	 * Initialized with seekbar layout and pre-populated title/message from resources.
	 * Uses immediate construction rather than lazy initialization for predictable
	 * resource allocation. Note: Despite the property name "downloadSpeedLimiterDialog",
	 * this dialog configures retry limits, not speed limits - indicating potential
	 * copy-paste code issue from GlobalDownloadSpeedLimit class.
	 */
	var downloadSpeedLimiterDialog: DialogBuilder = getBasicDialogBuilder(
		titleId = R.string.title_download_max_download_retry,
		messageId = R.string.text_download_max_download_retry
	)

	/**
	 * Displays the automatic retry limit configuration dialog.
	 *
	 * Configures the dialog with current retry limit values and interactive controls:
	 * 1. Retrieves current limit from globalAppSettings.downloadAutoResumeMaxErrors
	 * 2. Initializes seekbar with range 12-32 (reasonable retry attempt range)
	 * 3. Sets up real-time retry count preview using simple numeric formatting
	 * 4. Configures apply button handler with persistence logic
	 * 5. Shows dialog if not already visible
	 *
	 * Includes duplicate display prevention by checking isShowing flag before proceeding.
	 * Uses proper numeric formatting (unlike GlobalMaxParallelDownloads which incorrectly
	 * used speed formatting), providing clear UI feedback.
	 *
	 * Range Note: Minimum 12 retries ensures some persistence for temporary issues,
	 * while maximum 32 prevents infinite retry loops for permanent failures.
	 */
	fun showDialog() {
		// Prevent duplicate dialog instances - check if already showing
		if (downloadSpeedLimiterDialog.isShowing) return

		// Setting up the dialog layout and components
		val layout = downloadSpeedLimiterDialog.view
		val sliderPrev = layout.findViewById<TextView>(R.id.txt_seekbar_progress)
		val seekBar = layout.findViewById<SeekBar>(R.id.seekbar_slider)

		// Display the current retry limit in the text view
		// Uses simple numeric formatting - appropriate for retry counts
		val parallelConnections = aioSettings.downloadAutoResumeMaxErrors
		val seekbarPreview: String = parallelConnections.toString()

		sliderPrev.text = seekbarPreview

		// Set the seek bar's range and initial progress
		// Range 12-32 provides reasonable retry persistence options
		// Minimum 12 ensures basic retry capability for transient issues
		// Maximum 32 prevents excessive retry loops while allowing persistence
		seekBar.max = 32       // Maximum retry attempts (high persistence)
		seekBar.min = 12       // Minimum retry attempts (basic persistence)
		seekBar.progress = aioSettings.downloadAutoResumeMaxErrors

		// Listening for changes to the seek bar's value
		seekBar.setOnSeekBarChangeListener(object : SeekBarListener() {
			override fun onProgressChange(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				val progressValue = seekBar!!.progress
				// Simple numeric display - appropriate for retry counts
				sliderPrev.text = progressValue.toString()
			}
		})

		// Handling the apply and cancel buttons
		val btnApply = layout.findViewById<View>(R.id.btn_dialog_positive_container)
		val btnCancel = layout.findViewById<View>(R.id.button_dialog_negative_container)
		btnCancel.visibility = View.GONE  // Hide cancel button for simplified interaction
		// Single-action UI pattern: user either applies changes or dismisses via back button

		// Setting the button text
		layout.findViewById<TextView>(R.id.btn_dialog_positive)
			.text = CommonTextUtils.getText(R.string.title_apply_changes)

		btnCancel.setOnClickListener { _: View? ->
			if (downloadSpeedLimiterDialog.isShowing) {
				downloadSpeedLimiterDialog.close()
			}
		}

		btnApply.setOnClickListener { _: View? ->
			safeBaseActivityRef?.activityCoroutineScope
				?.launch {
					// Apply the new retry limit setting and save to storage
					aioSettings.downloadAutoResumeMaxErrors = seekBar.progress
					aioSettings.updateInDB()

					// Close the dialog
					if (downloadSpeedLimiterDialog.isShowing) downloadSpeedLimiterDialog.close()
					onApplyClick()  // Notify parent component of configuration change
					// Typically triggers download manager reconfiguration or UI refresh
				}
		}

		// Show the dialog if it's not already visible
		if (!downloadSpeedLimiterDialog.isShowing) {
			downloadSpeedLimiterDialog.show()
		}
	}

	/**
	 * Creates and configures a dialog builder with seekbar slider layout.
	 *
	 * Factory method that standardizes dialog creation with:
	 * 1. Specific layout (dialog_with_seekbar_slider_1) - shared with other configuration dialogs
	 * 2. Title and message text from resource IDs
	 * 3. Proper activity context binding
	 *
	 * Note: This method is duplicated across multiple dialog classes (GlobalDownloadSpeedLimit,
	 * GlobalMaxParallelDownloads, GlobalMaxAutoRetryLimit), indicating significant code
	 * duplication that should be refactored into a common base class or utility function.
	 *
	 * @param titleId Resource ID for dialog title text
	 *                (e.g., R.string.title_download_max_download_retry)
	 * @param messageId Resource ID for dialog description/instructions
	 *                  (e.g., R.string.text_download_max_download_retry)
	 * @return Configured DialogBuilder instance ready for further customization
	 */
	private fun getBasicDialogBuilder(titleId: Int, messageId: Int): DialogBuilder {
		val dialogBuilder = DialogBuilder(safeBaseActivityRef)
		dialogBuilder.setView(R.layout.dialog_with_seekbar_slider_1)

		val titleText = dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_title)
		val messageText = dialogBuilder.view.findViewById<View>(R.id.txt_dialog_message) as TextView

		titleText.setText(titleId)
		messageText.setText(messageId)
		return dialogBuilder
	}
}