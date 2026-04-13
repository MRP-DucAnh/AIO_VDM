package app.ui.main.fragments.downloads.fragments.active.dialogs

import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import app.core.bases.BaseActivity
import app.core.engines.downloader.AIODownload
import com.aio.R
import lib.networks.DownloaderUtils.getHumanReadableSpeed
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.DialogBuilder
import lib.ui.listeners.SeekBarListener
import java.lang.ref.WeakReference

/**
 * A utility class responsible for managing and displaying a dialog that allows users
 * to limit the download speed for a specific [AIODownload].
 *
 * This class provides a UI interface using a [SeekBar] to adjust the maximum network speed,
 * updates the application settings ([downloadSettings]), and persists the changes to storage.
 *
 * @property baseActivity The [BaseActivity] context used to build and display the dialog.
 * @property downloadModel The specific download data model associated with this speed limiter.
 * @property onApplyClick A callback invoked when the user apply the dialog.
 */
class DownloadSpeedLimiter(
	val baseActivity: BaseActivity?,
	val downloadModel: AIODownload,
	val onApplyClick: () -> Unit = {}
) {

	/**
	 * Logger instance for this class, used to record diagnostic messages and errors.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A weak reference to the [BaseActivity] to prevent memory leaks while
	 * allowing access to the context required for dialog construction.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Safely retrieves the underlying [BaseActivity] from the [WeakReference].
	 * Returns `null` if the activity has been reclaimed by the garbage collector.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfBaseActivity.get()

	/**
	 * Configuration settings related to download behavior and constraints.
	 * Used to manage and persist properties such as the maximum network speed limit.
	 */
	private val downloadSettings get() = downloadModel.config

	/**
	 * A builder instance responsible for constructing and managing the download speed limiter dialog.
	 * It is initialized with the basic layout, title, and message for setting the maximum
	 * network download speed.
	 */
	var downloadSpeedLimiterDialog: DialogBuilder = getBasicDialogBuilder(
		titleId = R.string.title_download_max_network_Speed,
		messageId = R.string.text_download_max_network_Speed
	)

	/**
	 * Displays the download speed limiter dialog to the user.
	 *
	 * This function initializes the dialog's UI components, including a seek bar for selecting
	 * the maximum network speed and a preview text displaying the selected speed.
	 * It handles seek bar progress changes, updates the speed setting in storage upon
	 * clicking "Apply", and executes the [onApplyClick] callback if the dialog is cancelled.
	 */
	fun showDialog() {
		if (downloadSpeedLimiterDialog.isShowing) return

		// Setting up the dialog layout and components
		val layout = downloadSpeedLimiterDialog.view
		val sliderPrev = layout.findViewById<TextView>(R.id.txt_seekbar_progress)
		val seekBar = layout.findViewById<SeekBar>(R.id.seekbar_slider)

		// Display the current download speed in the text view
		val speedBytesPerSecond = downloadSettings.downloadMaxNetworkSpeed.toDouble()
		val seekbarPreview: String = getHumanReadableSpeed(speedBytesPerSecond)

		sliderPrev.text = seekbarPreview

		// Set the seek bar's range and initial progress
		seekBar.max = 10485760  // Max value (e.g., 10 MB/s)
		seekBar.min = 0         // Min value (e.g., no limit)
		seekBar.progress = downloadSettings.downloadMaxNetworkSpeed.toInt()

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
			.text = getText(R.string.title_apply_changes)

		btnCancel.setOnClickListener { _: View? ->
			if (downloadSpeedLimiterDialog.isShowing) {
				downloadSpeedLimiterDialog.close()
			}
		}

		btnApply.setOnClickListener { _: View? ->
			// Apply the new speed setting and save to storage
			downloadSettings.downloadMaxNetworkSpeed = seekBar.progress.toLong()
			downloadModel.updateInDB()

			// Close the dialog
			if (downloadSpeedLimiterDialog.isShowing) downloadSpeedLimiterDialog.close()
			onApplyClick()
		}

		// Show the dialog if it's not already visible
		if (!downloadSpeedLimiterDialog.isShowing) {
			downloadSpeedLimiterDialog.show()
		}
	}

	/**
	 * Creates and initializes a [DialogBuilder] with a specific layout for seekbar-based interactions.
	 * Sets the title and message of the dialog using the provided resource IDs.
	 *
	 * @param titleId The resource ID for the title string.
	 * @param messageId The resource ID for the message string.
	 * @return A [DialogBuilder] instance configured with the custom layout and text.
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