package app.ui.others.media_player.dialogs

import android.view.*
import android.widget.*
import androidx.annotation.OptIn
import androidx.media3.common.util.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.downloadSystem
import app.core.bases.*
import app.core.engines.downloader.*
import app.ui.others.media_player.*
import com.aio.*
import kotlinx.coroutines.*
import lib.device.DateTimeUtils.millisToDateTimeString
import lib.files.*
import lib.files.VideoToAudioConverter.*
import lib.networks.DownloaderUtils.getHumanReadableFormat
import lib.process.*
import lib.process.CopyObjectUtils.deepCopy
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*

/**
 * A dialog utility object responsible for managing the MP4 to audio conversion workflow.
 *
 * This object provides a complete user interface and background processing solution for
 * converting video files (primarily MP4) to audio format (MP3). It handles:
 * - Showing a progress dialog with real-time conversion updates
 * - Managing the background conversion process
 * - Handling user cancellation
 * - Adding the converted audio file to the download system
 * - Updating the UI with the new converted file entry
 *
 * The conversion process uses a dedicated VideoToAudioConverter instance and provides
 * callbacks for progress, success, and failure scenarios. After successful conversion,
 * the audio file is registered in the media store and appears in the finished downloads
 * section with proper metadata.
 *
 * ## Key Features:
 * - **Progress Tracking**: Real-time percentage updates during conversion
 * - **Cancellation Support**: Users can cancel ongoing conversions
 * - **Media Player Integration**: Automatically pauses/resumes playback during conversion
 * - **Database Management**: Properly resets database IDs for the new download entry
 * - **Error Handling**: Comprehensive error handling with user feedback
 *
 * ## Usage Example:
 * ```
 * // Trigger conversion from any activity with a download model
 * Mp4ToAudioConverterDialog.showMp4ToAudioConverterDialog(activity, downloadModel)
 * ```
 */
object Mp4ToAudioConverterDialog {

	// Logger instance for tracking conversion events and debugging
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Displays the MP4 to audio conversion dialog and initiates the conversion process.
	 *
	 * This is the main entry point for converting a video file to audio format.
	 * It shows a progress dialog, manages the conversion lifecycle, and handles
	 * the UI updates throughout the process. The method performs the following steps:
	 * 1. Validates input parameters
	 * 2. Creates and displays a progress dialog
	 * 3. Sets up cancellation handling
	 * 4. Configures conversion callbacks
	 * 5. Initiates background conversion
	 * 6. Handles completion (success/failure)
	 *
	 * @param baseActivity The host activity that manages the dialog lifecycle.
	 *                     Must implement BaseActivity for proper coroutine scope management.
	 *                     If the activity is MediaPlayerActivity, playback is automatically
	 *                     paused during conversion and resumed afterward.
	 * @param downloadModel The download data model containing information about the video
	 *                      file to convert. Must have a valid destination file path.
	 * @throws IllegalArgumentException if either parameter is null
	 */
	@OptIn(UnstableApi::class)
	fun showMp4ToAudioConverterDialog(
		baseActivity: BaseActivity?,
		downloadModel: AIODownload?
	) {
		// Input validation - ensure both parameters are valid
		if (baseActivity == null) return
		if (downloadModel == null) return

		logger.d("Initializing MP4 to Audio conversion dialog")

		// Get coroutine scope for managing background/UI thread operations
		val coroutineScope = baseActivity.getAttachedCoroutineScope()

		// Initialize converter and progress dialog
		val videoToAudioConverter = VideoToAudioConverter()
		val loadingMessage = getText(R.string.title_converting_audio_progress_0)

		// Create progress dialog with cancellation disabled initially
		val conversionProgressDialog = WaitingDialog(
			baseActivityInf = baseActivity,
			loadingMessage = loadingMessage,
			isCancelable = false,           // Prevent accidental dismissal via back button
			shouldHideOkayButton = false    // Show cancel button for user control
		)

		// Configure the dialog's cancel button
		conversionProgressDialog.dialogBuilder?.view?.apply {
			findViewById<TextView>(R.id.btn_dialog_positive)?.apply {
				this.setText(R.string.title_cancel_converting)
				this.setLeftSideDrawable(R.drawable.ic_button_cancel)
			}

			// Set up cancellation listener
			findViewById<View>(R.id.btn_dialog_positive_container)
				?.setOnClickListener {
					logger.d("User clicked cancel during conversion")
					videoToAudioConverter.cancel()        // Stop the conversion process
					conversionProgressDialog.close()      // Dismiss the dialog
				}
		}

		// Display the progress dialog
		conversionProgressDialog.show()

		// If this is a media player activity, pause playback during conversion
		if (baseActivity is MediaPlayerActivity) {
			baseActivity.pausePlayback()
		}

		try {
			// Prepare file paths for conversion
			val inputMediaFilePath = downloadModel.getDestinationFile().absolutePath
			val convertedAudioFileName = downloadModel.fileName + "_converted.mp3"
			val outputPath = downloadModel.fileDirectory
			val outputMediaFile = File(outputPath, convertedAudioFileName)

			logger.d(
				"Starting audio extraction from: " +
					"$inputMediaFilePath -> $outputMediaFile"
			)

			// Success callback - executed when conversion completes successfully
			fun onSuccessUIUpdate(outputFile: String) {
				coroutineScope.launch(Dispatchers.Main) {
					try {
						logger.d("Conversion completed successfully: $outputFile")
						conversionProgressDialog.close()

						// Add file to Android's media store for gallery/player visibility
						FileSystemUtility.addToMediaStore(outputMediaFile)
						showToast(baseActivity, R.string.title_converted_successfully)

						// Add converted file to download system as a new entry
						addNewDownloadModelToSystem(downloadModel, outputMediaFile)

						// Resume playback if applicable
						if (baseActivity is MediaPlayerActivity) {
							baseActivity.resumePlayback()
						}
					} catch (error: Exception) {
						logger.e("Error adding converted file to the system:", error)
					}
				}
			}

			// Progress callback - updates the UI with conversion percentage
			fun onProgressUIUpdate(progress: Int) {
				coroutineScope.launch(Dispatchers.Main) {
					val progressTextView: TextView? = conversionProgressDialog.dialogBuilder
						?.view
						?.findViewById(R.id.txt_progress_info)

					if (progressTextView != null) {
						val resId = R.string.title_converting_audio_progress
						val progressString = INSTANCE.getString(resId, "$progress%")
						logger.d("Progress: $progress%")
						progressTextView.text = progressString
					} else {
						logger.d("Progress update skipped: Dialog view already released.")
					}
				}
			}

			// Failure callback - handles conversion errors
			fun onFailureUIUpdate(errorMessage: String) {
				coroutineScope.launch(Dispatchers.Main) {
					conversionProgressDialog.close()
					showToast(baseActivity, R.string.title_converting_failed)
					if (baseActivity is MediaPlayerActivity) {
						baseActivity.resumePlayback()
					}
				}
			}

			// Start the conversion process in a background thread
			coroutineScope.launch(Dispatchers.IO) {
				videoToAudioConverter.extractAudio(
					inputFile = inputMediaFilePath,
					outputFile = outputMediaFile.absolutePath,
					listener = object : ConversionListener {
						override fun onProgress(progress: Int) = onProgressUIUpdate(progress)
						override fun onSuccess(outputFile: String) = onSuccessUIUpdate(outputFile)
						override fun onFailure(errorMessage: String) = onFailureUIUpdate(errorMessage)
					}
				)
			}
		} catch (error: Exception) {
			// Handle any unexpected errors during setup
			logger.e("Unexpected error during conversion: ${error.message}")
			coroutineScope.launch(Dispatchers.Main) {
				conversionProgressDialog.close()
				showToast(baseActivity, R.string.title_something_went_wrong)
				if (baseActivity is MediaPlayerActivity) {
					baseActivity.resumePlayback()
				}
			}
		}
	}

	/**
	 * Creates a new download model for the converted audio file and adds it to the system.
	 *
	 * This method performs several critical operations:
	 * 1. Creates a deep copy of the original download model
	 * 2. Resets all database IDs to ensure it's treated as a new entity
	 * 3. Updates file metadata (name, path, size, timestamps)
	 * 4. Adds the model to the download system storage
	 * 5. Notifies the UI adapter to refresh the finished downloads list
	 *
	 * @param downloadDataModel The original video download model to use as a template.
	 *                          Must contain valid file information and metadata.
	 * @param outputMediaFile The converted audio file that was created.
	 *                        Must exist and be accessible for reading metadata.
	 */
	private fun addNewDownloadModelToSystem(
		downloadDataModel: AIODownload,
		outputMediaFile: File
	) {
		logger.d("Adding converted audio file to download system: ${outputMediaFile.name}")

		// Create a copy of the original model to avoid modifying the source
		val copiedModel = deepCopy(downloadDataModel) ?: return

		// Assign a unique download ID for tracking
		copiedModel.taskId = getUniqueNumberForDownloadModels()

		// Reset all ObjectBox database IDs to 0L so they're treated as new entities
		// This is crucial for database operations as 0L typically indicates a new record
		copiedModel.id = 0L

		// Reset VideoInfo and its foreign key IDs
		copiedModel.videoInfo?.let { videoInfo ->
			videoInfo.id = 0L
			videoInfo.downloadId = -1L  // Reset foreign key reference
			// Reset all associated video formats
			videoInfo.videoFormats.forEach { videoFormat ->
				videoFormat.id = 0L
				videoFormat.downloadId = -1L
				videoFormat.parentVideoInfoId = -1L
			}
		}

		// Reset VideoFormat (if separate from VideoInfo)
		copiedModel.videoFormat?.let { videoFormat ->
			videoFormat.id = 0L
			videoFormat.downloadId = -1L
			videoFormat.parentVideoInfoId = -1L
		}

		// Reset RemoteFileInfo and its foreign key ID
		copiedModel.remoteFileInfo?.let { remoteFileInfo ->
			remoteFileInfo.id = 0L
			remoteFileInfo.downloadId = -1L
		}

		// Reset AIOSettings and its foreign key ID
		copiedModel.config.let { settings ->
			settings.id = 0L
			settings.downloadId = -1L
		}

		// Update file-specific metadata
		copiedModel.fileName = outputMediaFile.name
		copiedModel.fileDirectory = outputMediaFile.parentFile?.absolutePath.toString()
		copiedModel.fileSize = outputMediaFile.length()
		copiedModel.fileSizeInFormat = getHumanReadableFormat(copiedModel.fileSize)
		copiedModel.fileCategoryName = copiedModel.getUpdatedCategoryName()

		// Set appropriate timestamps for the new download entry
		copiedModel.startTimeDate = System.currentTimeMillis()
		copiedModel.lastModifiedTimeDate = copiedModel.startTimeDate

		// Format timestamps for display
		val lastModifiedMillis = copiedModel.lastModifiedTimeDate
		copiedModel.startTimeDateInFormat = millisToDateTimeString(lastModifiedMillis)
		copiedModel.lastModifiedTimeDateInFormat = millisToDateTimeString(lastModifiedMillis)

		logger.d("Converted model prepared, updating storage and UI")

		// Persist the new model to database/storage
		copiedModel.updateInDB()

		// Add to the download system and trigger sorting
		downloadSystem.addAndSortFinishedDownloadDataModels(copiedModel)

		// Notify the UI adapter to refresh the finished downloads list
		downloadSystem
			.downloadsUIManager
			.finishedTasksFragment
			?.finishedTasksListAdapter
			?.notifyDataSetChangedOnSort(false)

		logger.d("Download system updated with converted file: ${copiedModel.fileName}")
	}
}