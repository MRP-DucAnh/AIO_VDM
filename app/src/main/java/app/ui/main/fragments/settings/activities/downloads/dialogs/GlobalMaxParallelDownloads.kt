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
 * Dialog for configuring maximum parallel download connections globally.
 *
 * Provides a user interface with a seekbar slider to set the maximum number of
 * simultaneous download operations allowed. This configuration helps balance
 * network resource utilization, prevent server overload, and optimize download
 * performance based on user preferences and network conditions.
 *
 * The parallel connection limit can range from 0 (no parallel downloads) up to
 * 10,485,760 (though practically limited by device and network capabilities).
 * Changes are immediately applied to AIOSettings and affect all subsequent
 * download operations globally.
 *
 * Features:
 * - Real-time connection count preview as slider moves
 * - Formatted display showing connection count
 * - Immediate persistence to application settings
 * - Memory-safe activity references using WeakReference
 * - Duplicate dialog prevention
 *
 * Note: The current implementation incorrectly uses speed formatting for
 * connection counts, which should be addressed for clarity.
 *
 * @property baseActivity BaseActivity context for UI operations (nullable for safety)
 * @property onApplyClick Callback invoked after successful configuration application
 *                       (typically for UI refresh or download manager notification)
 */
class GlobalMaxParallelDownloads(
	val baseActivity: BaseActivity?,
	val onApplyClick: () -> Unit = {}
) {

	/**
	 * Logger instance for tracking dialog lifecycle and user interactions.
	 *
	 * Records initialization, slider changes, apply operations, and any errors
	 * for debugging and user behavior analysis. Useful for identifying
	 * configuration patterns and troubleshooting dialog issues.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference wrapper for the base activity to prevent memory leaks.
	 *
	 * Safely holds activity reference without preventing garbage collection.
	 * Essential for dialogs that might outlive their parent activities, especially
	 * when activity can be null (as indicated by nullable parameter).
	 */
	private val weakReferenceOfActivity = WeakReference(baseActivity)

	/**
	 * Safe accessor for activity reference with null safety.
	 *
	 * Returns the activity if still available, null if garbage collected.
	 * All UI operations should check this property before proceeding to
	 * avoid NullPointerException in detached contexts.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfActivity.get()

	/**
	 * Direct accessor for global application settings.
	 *
	 * Provides read/write access to parallel download connection configuration
	 * through the injected AIOSettings instance. Used for both reading current
	 * values and persisting new selections.
	 */
	private val aioSettings get() = AIOSettingsRepo.getSettings()

	/**
	 * Dialog builder instance configured for parallel connection limit selection.
	 *
	 * Initialized with seekbar layout and pre-populated title/message from resources.
	 * Uses immediate construction rather than lazy initialization for predictable
	 * resource allocation, though dialog only displays when showDialog() is called.
	 */
	var downloadSpeedLimiterDialog: DialogBuilder = getBasicDialogBuilder(
		titleId = R.string.title_download_max_parallel_download,
		messageId = R.string.text_download_max_parrarel_download
	)

	/**
	 * Displays the parallel download limit configuration dialog.
	 *
	 * Configures the dialog with current parallel connection values and interactive controls:
	 * 1. Retrieves current limit from globalAppSettings.downloadDefaultParallelConnections
	 * 2. Initializes seekbar with range 0-10,485,760 (matching speed limit dialog range)
	 * 3. Sets up real-time connection count preview (though incorrectly using speed formatting)
	 * 4. Configures apply button handler with persistence logic
	 * 5. Shows dialog if not already visible
	 *
	 * Includes duplicate display prevention by checking isShowing flag before proceeding.
	 *
	 * Note: Currently uses speed formatting functions for connection counts, which
	 * creates misleading UI. Should be refactored to use connection count formatting.
	 */
	fun showDialog() {
		// Prevent duplicate dialog instances
		if (downloadSpeedLimiterDialog.isShowing) return

		// Setting up the dialog layout and components
		val layout = downloadSpeedLimiterDialog.view
		val sliderPrev = layout.findViewById<TextView>(R.id.txt_seekbar_progress)
		val seekBar = layout.findViewById<SeekBar>(R.id.seekbar_slider)

		// Display the current parallel connection count in the text view
		val parallelConnections = aioSettings.downloadDefaultParallelConnections
		val seekbarPreview: String = parallelConnections.toString()

		sliderPrev.text = seekbarPreview

		// Set the seek bar's range and initial progress
		// Practical connection limits are typically 1-20 for most applications
		seekBar.max = 24        // Max value 24 (24 parallel downloads)
		seekBar.min = 1         // Min value (0 = no parallel downloads)
		seekBar.progress = aioSettings.downloadDefaultParallelConnections

		// Listening for changes to the seek bar's value
		seekBar.setOnSeekBarChangeListener(object : SeekBarListener() {
			override fun onProgressChange(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				val progressValue = seekBar!!.progress
				sliderPrev.text = progressValue.toString()
			}
		})

		// Handling the apply and cancel buttons
		val btnApply = layout.findViewById<View>(R.id.btn_dialog_positive_container)
		val btnCancel = layout.findViewById<View>(R.id.button_dialog_negative_container)
		btnCancel.visibility = View.GONE  // Hide cancel button for simplified interaction

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
					// Apply the new connection limit setting and save to storage
					aioSettings.downloadDefaultParallelConnections = seekBar.progress
					aioSettings.updateInDB()

					// Close the dialog
					if (downloadSpeedLimiterDialog.isShowing) downloadSpeedLimiterDialog.close()
					onApplyClick()  // Notify parent component of configuration change
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
	 * 1. Specific layout (dialog_with_seekbar_slider_1) - shared with speed limit dialog
	 * 2. Title and message text from resource IDs
	 * 3. Proper activity context binding
	 *
	 * Note: This method is shared with GlobalDownloadSpeedLimit class, indicating
	 * code duplication that could be refactored into a common base class or utility.
	 *
	 * @param titleId Resource ID for dialog title text
	 *                (e.g., R.string.title_download_max_parallel_download)
	 * @param messageId Resource ID for dialog description/instructions
	 *                  (e.g., R.string.text_download_max_parrarel_download)
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