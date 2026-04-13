package app.ui.main.fragments.settings.activities.downloads.dialogs

import android.view.*
import android.widget.*
import app.core.bases.*
import app.core.engines.settings.*
import com.aio.*
import kotlinx.coroutines.*
import lib.networks.DownloaderUtils.getHumanReadableSpeed
import lib.process.*
import lib.texts.*
import lib.ui.builders.*
import lib.ui.listeners.*
import java.lang.ref.*

/**
 * Dialog for configuring global download speed limits across the application.
 *
 * Provides a user interface with a seekbar slider to set maximum network bandwidth
 * for download operations. This helps users manage network resource allocation,
 * prevent excessive bandwidth consumption, and comply with network policies.
 *
 * The speed limit is measured in bytes per second and can range from 0 (unlimited)
 * up to 10,485,760 bytes/second (10 MB/s). Changes are immediately applied to
 * AIOSettings and affect all subsequent download operations globally.
 *
 * Features:
 * - Real-time speed preview as slider moves
 * - Clear visual feedback with formatted speed display
 * - Immediate persistence to application settings
 * - Memory-safe activity references
 *
 * @property baseActivity BaseActivity context for UI operations (nullable for safety)
 * @property onApplyClick Callback invoked after successful speed limit application
 *                       (typically for UI refresh or notification)
 */
class GlobalDownloadSpeedLimit(
	val baseActivity: BaseActivity?,
	val onApplyClick: () -> Unit = {}
) {

	/**
	 * Logger instance for tracking dialog lifecycle and user interactions.
	 *
	 * Records initialization, slider changes, apply operations, and any errors
	 * for debugging and user behavior analysis.
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
	 * Provides read/write access to download speed limit configuration
	 * through the injected AIOSettings instance.
	 */
	private val aioSettings get() = AIOSettingsRepo.getSettings()

	/**
	 * Dialog builder instance configured for speed limit selection.
	 *
	 * Initialized with seekbar layout and pre-populated title/message.
	 * Uses lazy dialog construction pattern to defer resource allocation
	 * until first display.
	 */
	var downloadSpeedLimiterDialog: DialogBuilder = getBasicDialogBuilder(
		titleId = R.string.title_download_max_network_Speed,
		messageId = R.string.text_download_max_network_Speed
	)

	/**
	 * Displays the download speed limit configuration dialog.
	 *
	 * Configures the dialog with current speed limit values and interactive controls:
	 * 1. Seeks current limit from globalAppSettings
	 * 2. Initializes seekbar with appropriate range (0-10MB/s)
	 * 3. Sets up real-time speed preview updates
	 * 4. Configures apply/cancel button handlers
	 * 5. Shows dialog if not already visible
	 *
	 * Includes duplicate display prevention by checking isShowing flag.
	 */
	fun showDialog() {
		// Prevent duplicate dialog instances
		if (downloadSpeedLimiterDialog.isShowing) return

		// Setting up the dialog layout and components
		val layout = downloadSpeedLimiterDialog.view
		val sliderPrev = layout.findViewById<TextView>(R.id.txt_seekbar_progress)
		val seekBar = layout.findViewById<SeekBar>(R.id.seekbar_slider)

		// Display the current download speed in the text view
		val speedBytesPerSecond = aioSettings.downloadMaxNetworkSpeed.toDouble()
		val seekbarPreview: String = getHumanReadableSpeed(speedBytesPerSecond)

		sliderPrev.text = seekbarPreview

		// Set the seek bar's range and initial progress
		seekBar.max = 10485760  // Max value (10 MB/s = 10 * 1024 * 1024 bytes)
		seekBar.min = 0         // Min value (0 = unlimited/no limit)
		seekBar.progress = aioSettings.downloadMaxNetworkSpeed.toInt()

		// Listening for changes to the seek bar's value
		seekBar.setOnSeekBarChangeListener(object : SeekBarListener() {
			override fun onProgressChange(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				val speed = seekBar!!.progress
				sliderPrev.text = getHumanReadableSpeed(speed.toDouble())
			}
		})

		// Handling the apply and cancel buttons
		val btnApply = layout.findViewById<View>(R.id.btn_dialog_positive_container)
		val btnCancel = layout.findViewById<View>(R.id.button_dialog_negative_container)
		btnCancel.visibility = View.GONE

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
					// Apply the new speed setting and save to storage
					aioSettings.downloadMaxNetworkSpeed = seekBar.progress.toLong()
					aioSettings.updateInDB()

					// Close the dialog
					if (downloadSpeedLimiterDialog.isShowing) {
						downloadSpeedLimiterDialog.close()
					}
					onApplyClick()
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
	 * 1. Specific layout (dialog_with_seekbar_slider_1)
	 * 2. Title and message text from resource IDs
	 * 3. Proper activity context binding
	 *
	 * @param titleId Resource ID for dialog title text
	 *                (e.g., R.string.title_download_max_network_Speed)
	 * @param messageId Resource ID for dialog description/instructions
	 *                  (e.g., R.string.text_download_max_network_Speed)
	 * @return Configured DialogBuilder instance ready for further customization
	 */
	private fun getBasicDialogBuilder(titleId: Int, messageId: Int): DialogBuilder {
		val dialogBuilder = DialogBuilder(safeBaseActivityRef)
		dialogBuilder.setView(R.layout.dialog_with_seekbar_slider_1)

		val titleText = dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_title)
		val messageText = dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_message)

		titleText.setText(titleId)
		messageText.setText(messageId)
		return dialogBuilder
	}
}