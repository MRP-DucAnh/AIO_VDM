package app.ui.others.media_player.dialogs

import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.downloadSystem
import app.core.bases.BaseActivity
import app.core.engines.downloader.DownloadDataModel
import app.ui.others.media_player.MediaPlayerActivity
import com.aio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.DateTimeUtils.millisToDateTimeString
import lib.files.FileSystemUtility
import lib.files.VideoToAudioConverter
import lib.files.VideoToAudioConverter.ConversionListener
import lib.networks.DownloaderUtils.getHumanReadableFormat
import lib.process.CopyObjectUtils.deepCopy
import lib.process.LogHelperUtils
import lib.process.UniqueNumberUtils.getUniqueNumberForDownloadModels
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.builders.ToastView.Companion.showToast
import lib.ui.builders.WaitingDialog
import java.io.File

object Mp4ToAudioConverterDialog {

	private val logger = LogHelperUtils.from(javaClass)

	@OptIn(UnstableApi::class)
	fun showMp4ToAudioConverterDialog(
		baseActivityRef: BaseActivity?,
		downloadModel: DownloadDataModel?
	) {
		if (baseActivityRef == null) return
		if (downloadModel == null) return
		val activityRef = baseActivityRef
		val dataModel = downloadModel

		logger.d("Initializing MP4 to Audio conversion dialog")
		val coroutineScope = CoroutineScope(Dispatchers.IO)
		val videoToAudioConverter = VideoToAudioConverter(coroutineScope)
		val loadingMessage = getText(R.string.title_converting_audio_progress_0)
		var loadingMessageTv: TextView? = null
		val waitingDialog = WaitingDialog(
			baseActivityInf = activityRef,
			loadingMessage = loadingMessage,
			isCancelable = false,
			shouldHideOkayButton = false
		)

		waitingDialog.dialogBuilder?.view?.apply {
			loadingMessageTv = findViewById(R.id.txt_progress_info)

			findViewById<TextView>(R.id.btn_dialog_positive)?.apply {
				this.setText(R.string.title_cancel_converting)
				this.setLeftSideDrawable(R.drawable.ic_button_cancel)
			}

			findViewById<View>(R.id.btn_dialog_positive_container)
				?.setOnClickListener {
					logger.d("User clicked cancel during conversion")
					videoToAudioConverter.cancel()
					coroutineScope.cancel()
					waitingDialog.close()
				}
		}

		if (activityRef is MediaPlayerActivity) activityRef.pausePlayback()
		waitingDialog.show()

		try {
			val inputMediaFilePath = dataModel.getDestinationFile().absolutePath
			val convertedAudioFileName = dataModel.fileName + "_converted.mp3"
			val outputPath = dataModel.fileDirectory
			val outputMediaFile = File(outputPath, convertedAudioFileName)

			logger.d("Starting audio extraction from: " +
				"$inputMediaFilePath -> $outputMediaFile")

			@Synchronized
			fun onSuccessUIUpdate(outputFile: String) {
				coroutineScope.launch {
					withContext(Dispatchers.Main) {
						try {
							logger.d("Conversion completed successfully: $outputFile")
							waitingDialog.close()

							if (activityRef is MediaPlayerActivity) activityRef.resumePlayback()

							FileSystemUtility.addToMediaStore(outputMediaFile)
							showToast(activityRef, R.string.title_converted_successfully)

							addNewDownloadModelToSystem(dataModel, outputMediaFile)
						} catch (error: Exception) {
							logger.e("Error adding converted file to the system: " +
								"${error.message}")
						}
					}
				}
			}

			@Synchronized
			fun onProgressUIUpdate(progress: Int) {
				coroutineScope.launch {
					withContext(Dispatchers.Main) {
						val resId = R.string.title_converting_audio_progress
						val progressString = INSTANCE.getString(resId, "$progress%")
						logger.d("Progress: $progress%")
						loadingMessageTv?.text = progressString
					}
				}
			}

			@Synchronized
			fun onFailureUIUpdate(errorMessage: String) {
				coroutineScope.launch {
					withContext(Dispatchers.Main) {
						waitingDialog.close()
						if (activityRef is MediaPlayerActivity) activityRef.resumePlayback()
						showToast(activityRef, R.string.title_converting_failed)
					}
				}
			}

			videoToAudioConverter.extractAudio(
				inputFile = inputMediaFilePath,
				outputFile = outputMediaFile.absolutePath,
				listener = object : ConversionListener {
					override fun onProgress(progress: Int) = onProgressUIUpdate(progress)
					override fun onSuccess(outputFile: String) = onSuccessUIUpdate(outputFile)
					override fun onFailure(errorMessage: String) = onFailureUIUpdate(errorMessage)
				}
			)
		} catch (error: Exception) {
			logger.e("Unexpected error during conversion: ${error.message}")
			coroutineScope.launch {
				withContext(Dispatchers.Main) {
					waitingDialog.close()
					if (activityRef is MediaPlayerActivity) activityRef.resumePlayback()
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	private fun addNewDownloadModelToSystem(
		downloadDataModel: DownloadDataModel,
		outputMediaFile: File
	) {
		logger.d("Adding converted audio file to download system: ${outputMediaFile.name}")

		val copiedModel = deepCopy(downloadDataModel) ?: return
		copiedModel.downloadId = getUniqueNumberForDownloadModels()
		copiedModel.fileName = outputMediaFile.name
		copiedModel.fileDirectory = outputMediaFile.parentFile?.absolutePath.toString()
		copiedModel.fileSize = outputMediaFile.length()
		copiedModel.fileSizeInFormat = getHumanReadableFormat(copiedModel.fileSize)
		copiedModel.fileCategoryName = copiedModel.getUpdatedCategoryName()

		copiedModel.startTimeDate = System.currentTimeMillis()
		copiedModel.lastModifiedTimeDate = copiedModel.startTimeDate

		val t = copiedModel.lastModifiedTimeDate
		copiedModel.startTimeDateInFormat = millisToDateTimeString(t)
		copiedModel.lastModifiedTimeDateInFormat = millisToDateTimeString(t)

		logger.d("Converted model prepared, updating storage and UI")

		copiedModel.updateInStorage()
		downloadSystem.addAndSortFinishedDownloadDataModels(copiedModel)

		downloadSystem
			.downloadsUIManager
			.finishedTasksFragment
			?.finishedTasksListAdapter
			?.notifyDataSetChangedOnSort(false)

		logger.d("Download system updated with converted file: ${copiedModel.fileName}")
	}
}