package app.core.engines.downloader

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOApp.Companion.internalDataFolder
import app.core.engines.downloader.AIODownload.Companion.THUMB_EXTENSION
import app.core.engines.downloader.DownloadStatus.DOWNLOADING
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import com.aio.R
import com.anggrayudi.storage.file.getAbsolutePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isVideoByName
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.getText
import lib.ui.MsgDialogUtils
import lib.ui.ViewUtility.animateFadInOutAnim
import lib.ui.ViewUtility.closeAnyAnimation
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.isBlackThumbnail
import lib.ui.ViewUtility.normalizeTallSymbols
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.showView
import lib.ui.ViewUtility.shrinkTextToFitView
import java.io.File
import java.lang.ref.WeakReference

class DownloaderRowUIManager(downloadRowView: View) {

	private val logger = LogHelperUtils.from(javaClass)
	private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val weakReferenceOfLayout = WeakReference(downloadRowView)
	private var isShowingAnyDialog = false
	private var cachedThumbLoaded = false
	private var isThumbnailSettingsChanged = false
	private val safeLayoutRef: View? get() = weakReferenceOfLayout.get()

	private val thumbnailView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_file_thumbnail) }
	private val statusIconView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_status_indicator) }
	private val fileNameTxtView: TextView? by lazy { safeLayoutRef?.findViewById(R.id.txt_file_name) }
	private val statusTextView: TextView? by lazy { safeLayoutRef?.findViewById(R.id.txt_download_status) }
	private val siteFaviconView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_site_favicon) }
	private val mediaDurationView: TextView? by lazy { safeLayoutRef?.findViewById(R.id.txt_media_duration) }
	private val mediaDurationContainer: View? by lazy { safeLayoutRef?.findViewById(R.id.container_media_duration) }
	private val fileTypeIconView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_file_type_indicator) }
	private val mediaPlayIconView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_media_play_indicator) }
	private val privateFolderIconView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_private_folder_indicator) }
	private val activeIndicatorImgView: ImageView? by lazy { safeLayoutRef?.findViewById(R.id.img_active_indicator) }

	fun clearResources() {
		coroutineScope.coroutineContext.cancelChildren()
		weakReferenceOfLayout.clear()
		cachedThumbLoaded = false
		isThumbnailSettingsChanged = false
		isShowingAnyDialog = false
		thumbnailView?.setImageDrawable(null)
		siteFaviconView?.setImageDrawable(null)
	}

	fun updateView(downloadModel: AIODownload) {
		coroutineScope.coroutineContext.cancelChildren()
		logger.d("UI update: id=${downloadModel.taskId}, status=${downloadModel.downloadStatus}")
		safeLayoutRef?.let {
			updateRowVisibility(downloadModel)
			updateFileNameLabel(downloadModel)
			refreshProgressState(downloadModel)
			coroutineScope.launch {
				refreshThumbnail(downloadModel)
				refreshFavicon(downloadModel)
				refreshMediaDuration(downloadModel)
			}
			refreshMediaPlayIcon(downloadModel)
			refreshFileTypeIcon(downloadModel)
			refreshPrivateFolderIcon(downloadModel)
			refreshActiveIndicator(downloadModel)
			showDownloadErrorDialog(downloadModel)
		} ?: logger.d("Row ref lost, skipping update")
	}

	private fun updateRowVisibility(downloadModel: AIODownload) {
		safeLayoutRef?.let {
			logger.d(
				"Visibility: id=${downloadModel.taskId}, " +
					"removed=${downloadModel.isRemoved}, complete=${downloadModel.isComplete}"
			)

			if (downloadModel.isRemoved ||
				downloadModel.isComplete ||
				downloadModel.isWentToPrivateFolder ||
				downloadModel.config.hideDownloadProgressFromUI
			) {
				if (it.visibility != GONE) {
					logger.d("Hiding row: id=${downloadModel.taskId}")
					it.visibility = GONE
				}
			}
		}
	}

	private fun updateFileNameLabel(downloadModel: AIODownload) {
		val currentFileName = fileNameTxtView?.text.toString()
		val fileNameFromDataModel = downloadModel.fileName
		if (currentFileName.equals(fileNameFromDataModel, true)) return

		fileNameTxtView?.text = fileNameFromDataModel.ifEmpty {
			logger.d("No filename for id=${downloadModel.taskId}, showing placeholder")
			getText(R.string.title_getting_name_from_server)
		}

		fileNameTxtView?.normalizeTallSymbols()
	}

	private fun refreshProgressState(downloadModel: AIODownload) {
		logger.d("Progress: id=${downloadModel.taskId}, " +
			"${downloadModel.progressPercentage}% ${downloadModel.downloadStatus}")
		renderProgressDetails(downloadModel)
	}

	private fun renderProgressDetails(downloadModel: AIODownload) {
		if (statusTextView == null) return
		if (statusTextView?.context == null) return

		if (downloadModel.downloadStatus != DOWNLOADING && downloadModel.ytdlpErrorMessage.isNotEmpty()) {
			logger.d("yt-dlp error for id=${downloadModel.taskId}: ${downloadModel.ytdlpErrorMessage}")
			statusTextView?.text = downloadModel.ytdlpErrorMessage
			statusTextView?.setTextColor(statusTextView!!.context.getColor(R.color.color_error))
		} else {
			val infoInString = downloadModel.getFormattedDownloadStatusInfo()
			statusTextView?.post { shrinkTextToFitView(statusTextView, infoInString, "|  --:-- ") }
			statusTextView?.setTextColor(statusTextView!!.context.getColor(R.color.color_text_hint))
		}
	}

	private suspend fun refreshThumbnail(downloadModel: AIODownload) {
		withContext(Dispatchers.Default) {
			val settings = downloadModel.config
			val shouldHideThumbnailSetting = settings.downloadHideVideoThumbnail

			if (shouldHideVideoThumbnail(downloadModel)) {
				val defaultThumbResource = R.drawable.image_no_thumb_available
				val resources = weakReferenceOfLayout.get()?.context?.resources
				val defaultThumbDrawable = ResourcesCompat.getDrawable(
					resources ?: INSTANCE.resources, defaultThumbResource, null
				)

				logger.d("Video thumbnails not allowed, using default for id=${downloadModel.taskId}")
				withContext(Dispatchers.Main) {
					thumbnailView?.setImageDrawable(defaultThumbDrawable)
					thumbnailView?.tag = false
					isThumbnailSettingsChanged = shouldHideThumbnailSetting
				}
				return@withContext
			}

			if (shouldHideThumbnailSetting != isThumbnailSettingsChanged) {
				isThumbnailSettingsChanged = true
			}

			if (thumbnailView?.tag == null || isThumbnailSettingsChanged) {
				val imageUri = downloadModel.getThumbnailURI()

				if (!shouldHideThumbnailSetting && imageUri != null) {
					logger.d("Setting actual thumbnail URI for id=${downloadModel.taskId}")
					withContext(Dispatchers.Main) {
						thumbnailView?.setImageURI(imageUri)
						thumbnailView?.tag = true
						isThumbnailSettingsChanged = shouldHideThumbnailSetting
					}
				} else {
					logger.d("Using default thumbnail logic or hiding for id=${downloadModel.taskId}")
					loadDefaultThumbnail(downloadModel)
				}
			}
		}
	}

	// This remains synchronous as it's a fast UI update based on the model.
	private fun refreshFileTypeIcon(downloadDataModel: AIODownload) {
		logger.d("Updating file type indicator for download ID: ${downloadDataModel.taskId}")
		fileTypeIconView?.setImageResource(
			when {
				isImageByName(downloadDataModel.fileName) -> R.drawable.ic_button_images
				isAudioByName(downloadDataModel.fileName) -> R.drawable.ic_button_audio
				isVideoByName(downloadDataModel.fileName) -> R.drawable.ic_button_video
				isDocumentByName(downloadDataModel.fileName) -> R.drawable.ic_button_document
				isArchiveByName(downloadDataModel.fileName) -> R.drawable.ic_button_archives
				isProgramByName(downloadDataModel.fileName) -> R.drawable.ic_button_programs
				else -> R.drawable.ic_button_file
			}
		)
	}

	private suspend fun refreshMediaDuration(downloadDataModel: AIODownload) {
		withContext(Dispatchers.IO) {
			val fileName = downloadDataModel.fileName
			if (isVideoByName(fileName) || isAudioByName(fileName)) {
				val playbackDuration = downloadDataModel.mediaFilePlaybackDuration
				val cleanedData = playbackDuration.replace("(", "").replace(")", "")

				withContext(Dispatchers.Main) {
					if (cleanedData.isNotEmpty() && !cleanedData.contentEquals("--:--", true)) {
						showView(mediaDurationContainer, true)
						mediaDurationView?.text = cleanedData
					} else {
						hideView(mediaDurationContainer, shouldAnimate = false)
					}
				}
			} else {
				withContext(Dispatchers.Main) {
					mediaDurationContainer?.visibility = GONE
				}
			}
		}
	}

	private fun refreshMediaPlayIcon(downloadDataModel: AIODownload) {
		val fileName = downloadDataModel.fileName
		if (isVideoByName(fileName) || isAudioByName(fileName))
			mediaPlayIconView?.visibility = VISIBLE
		else mediaPlayIconView?.visibility = GONE
	}

	private suspend fun refreshFavicon(downloadDataModel: AIODownload) {
		withContext(Dispatchers.IO) {
			logger.d("Updating favicon for download ID: ${downloadDataModel.taskId}")
			val defaultFaviconResId = R.drawable.ic_image_default_favicon
			val resources = weakReferenceOfLayout.get()?.context?.resources
			val defaultFaviconDrawable = ResourcesCompat.getDrawable(
				resources ?: INSTANCE.resources, defaultFaviconResId, null
			)

			if (shouldHideVideoThumbnail(downloadDataModel)) {
				logger.d("Video thumbnails not allowed, using default favicon")
				withContext(Dispatchers.Main) {
					siteFaviconView?.setImageDrawable(defaultFaviconDrawable)
				}
				return@withContext
			}

			try {
				val referralSite = downloadDataModel.siteReferrer
				logger.d("Loading favicon for site: $referralSite")

				aioFavicons.getFavicon(referralSite)?.let { faviconFilePath ->
					val faviconImgFile = File(faviconFilePath)

					if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
						logger.d("Favicon file not found"); return@let
					}

					val faviconImgURI = faviconImgFile.toUri()
					withContext(Dispatchers.Main) {
						try {
							logger.d("Setting favicon from URI")
							showView(siteFaviconView, true)
							siteFaviconView?.setImageURI(faviconImgURI)
						} catch (error: Exception) {
							logger.d("Error setting favicon: ${error.message}")
							error.printStackTrace()
							showView(siteFaviconView, true)
							siteFaviconView?.setImageResource(defaultFaviconResId)
						}
					}
				}
			} catch (error: Exception) {
				logger.e("Error loading favicon: ${error.message}", error)
				withContext(Dispatchers.Main) {
					siteFaviconView?.setImageDrawable(defaultFaviconDrawable)
				}
			}
		}
	}

	private fun shouldHideVideoThumbnail(downloadDataModel: AIODownload): Boolean {
		val model = downloadDataModel
		val globalSettings = model.config
		val isVideoHidden = globalSettings.downloadHideVideoThumbnail
		val downloadFile = model.getDestinationDocumentFile()
		val result = isVideo(downloadFile) && isVideoHidden
		logger.d("Video thumbnail allowed: ${!result}")
		return result
	}

	private suspend fun loadDefaultThumbnail(downloadModel: AIODownload) {
		withContext(Dispatchers.IO) {
			if (downloadModel.isUnknownFileSize) {
				withContext(Dispatchers.Main) {
					displayDefaultThumbnailIcon(downloadModel)
					thumbnailView?.tag = true
				}
				return@withContext
			}

			val videoInfo = downloadModel.videoInfo
			val videoFormat = downloadModel.videoFormat
			val thumbPath = downloadModel.thumbPath

			if (downloadModel.progressPercentage > 5 ||
				videoInfo?.videoThumbnailUrl != null ||
				downloadModel.thumbnailUrl.isNotEmpty()) {

				if (cachedThumbLoaded) return@withContext

				val defaultThumb = downloadModel.getThumbnailDrawableID()
				val cachedThumbPath = thumbPath

				if (cachedThumbPath.isNotEmpty() && File(cachedThumbPath).exists()) {
					withContext(Dispatchers.Main) {
						loadBitmapToImageView(thumbPath, defaultThumb)
						cachedThumbLoaded = true
					}
					return@withContext
				} else {
					val videoDestinationFile = if (videoInfo != null && videoFormat != null) {
						val ytdlpDestinationFilePath = downloadModel.ytdlpTempOutputPath
						val ytdlpId = File(ytdlpDestinationFilePath).name
						var destinationFile = File(ytdlpDestinationFilePath)

						internalDataFolder.listFiles().forEach { file ->
							try {
								file?.let {
									if (!file.isFile) return@let
									if (file.name!!.startsWith(ytdlpId)
										&& file.name!!.endsWith("part")) {
										destinationFile = File(file.getAbsolutePath(INSTANCE))
									}
								}
							} catch (error: Exception) {
								logger.e("Error processing file:", error)
							}
						}
						destinationFile
					} else {
						downloadModel.getDestinationFile()
					}

					val thumbnailUrl = videoInfo?.videoThumbnailUrl ?: downloadModel.thumbnailUrl
					val bitmap = getThumbnailFromFile(videoDestinationFile, thumbnailUrl, 420)

					if (bitmap != null) {
						val rotatedBitmap = if (bitmap.height > bitmap.width) {
							rotateBitmap(bitmap, 270f)
						} else bitmap

						val thumbnailName = "${downloadModel.taskId}$THUMB_EXTENSION"
						saveBitmapToFile(bitmapToSave = rotatedBitmap,
							fileName = thumbnailName)?.let { filePath ->
							if (!isBlackThumbnail(File(filePath))) {
								downloadModel.thumbPath = filePath
								downloadModel.updateInDB()
								withContext(Dispatchers.Main) {
									loadBitmapToImageView(
										thumbFilePath = thumbPath,
										defaultThumb = defaultThumb
									)
								}
							}
						}
					}
				}
			} else {
				withContext(Dispatchers.Main) {
					displayDefaultThumbnailIcon(downloadModel)
				}
			}
		}
	}

	private fun loadBitmapToImageView(thumbFilePath: String, defaultThumb: Int) {
		try {
			thumbnailView?.setImageURI(File(thumbFilePath).toUri())
		} catch (error: Exception) {
			logger.e("Error loading thumbnail: ${error.message}", error)
			thumbnailView?.setImageResource(defaultThumb)
		}
	}

	private fun displayDefaultThumbnailIcon(downloadModel: AIODownload) {
		logger.d("Showing default thumb for id=${downloadModel.taskId}")
		val context = weakReferenceOfLayout.get()?.context ?: INSTANCE
		val drawableID = downloadModel.getThumbnailDrawableID()
		val drawable = getDrawable(context, drawableID)
		thumbnailView?.setImageDrawable(drawable)
	}

	private fun refreshPrivateFolderIcon(downloadModel: AIODownload) {
		logger.d("Updating private folder indicator UI state")
		val downloadLocation = downloadModel.config.defaultDownloadLocationType
		logger.d("Current download location: $downloadLocation")
		privateFolderIconView?.setImageResource(
			when (downloadLocation) {
				PRIVATE_FOLDER -> R.drawable.ic_button_lock
				else -> R.drawable.ic_button_folder
			}
		)
	}

	private fun refreshActiveIndicator(downloadModel: AIODownload) {
		logger.d("Updating download active and pause state indicator")
		activeIndicatorImgView?.visibility = if (downloadModel.isRunning) VISIBLE else GONE
		if (downloadModel.isRunning) animateFadInOutAnim(activeIndicatorImgView)
		else closeAnyAnimation(activeIndicatorImgView)
	}

	private fun showDownloadErrorDialog(downloadDataModel: AIODownload) {
		if (downloadDataModel.userAlertMessage.isNotEmpty()) {
			logger.d("Showing error dialog for id=${downloadDataModel.taskId}")

			downloadSystem.downloadsUIManager.activeTasksFragment?.let {
				if (!isShowingAnyDialog) {
					MsgDialogUtils.showMessageDialog(
						baseActivityInf = it.safeBaseActivityRef,
						titleText = getText(R.string.title_download_failed),
						isTitleVisible = true,
						isCancelable = false,
						messageTxt = downloadDataModel.userAlertMessage,
						isNegativeButtonVisible = false,
						dialogBuilderCustomize = { dialogBuilder ->
							isShowingAnyDialog = true
							dialogBuilder.setOnClickForPositiveButton {
								dialogBuilder.close()
								this@DownloaderRowUIManager.isShowingAnyDialog = false
								downloadDataModel.userAlertMessage = ""
								downloadDataModel.updateInDB()
							}
						}
					)
				}
			}
		}
	}
}