package app.ui.main.fragments.downloads.fragments.active

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.downloadSystem
import app.core.engines.caches.LoginSessionCache.hasRecentLoginSessionForHost
import app.core.engines.downloader.AIODownload
import app.core.engines.downloader.AIODownload.Companion.THUMB_EXTENSION
import app.core.engines.downloader.DownloadStatus.DOWNLOADING
import app.core.engines.downloader.DownloadTaskInf
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.video_parser.dialogs.VideoLinkPasteEditor
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.dialogs.DownloadFileRenamer
import app.ui.main.fragments.downloads.dialogs.DownloadInfoTracker
import app.ui.main.fragments.downloads.fragments.active.dialogs.DownloadSpeedLimiter
import app.ui.main.fragments.downloads.fragments.active.dialogs.HttpUserAgentSelector
import app.ui.main.fragments.downloads.fragments.active.dialogs.LoginRequiredDialog
import com.aio.R
import com.aio.R.layout
import com.aio.R.string
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.ShareUtility.shareUrl
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isVideoByName
import lib.networks.URLUtility.isValidURL
import lib.networks.URLUtilityKT.getHostFromUrl
import lib.process.LogHelperUtils
import lib.texts.ClipboardUtils.copyTextToClipboard
import lib.texts.CommonTextUtils.getText
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.MsgDialogUtils.showMessageDialog
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.normalizeTallSymbols
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.setRightSideDrawable
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.io.File
import java.lang.ref.WeakReference

/**
 * Manages and displays the options dialog for active download tasks.
 *
 * This class handles various user interactions for ongoing downloads, including:
 * - Resuming, pausing, removing, and deleting tasks.
 * - Renaming files and toggling metadata visibility (e.g., thumbnails).
 * - Modifying task-specific settings like Wi-Fi only mode and notification preferences.
 * - Copying or sharing download and referrer URLs.
 * - Accessing detailed task information and alternative download resolutions.
 *
 * It uses a [DialogBuilder] to present a bottom-sheet or modal interface and maintains
 * a [WeakReference] to the [MotherActivity] to prevent memory leaks while interacting
 * with the UI and the download system.
 *
 * @property motherActivity The parent activity context used to initialize the dialog and access UI components.
 */
class ActiveTasksOptions(private val motherActivity: MotherActivity?) {

	/**
	 * Logger instance used for tracking events and debugging information for [ActiveTasksOptions].
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A weak reference to the [MotherActivity] to prevent memory leaks while
	 * providing access to the activity context when it is available.
	 */
	private val activityWeakRef = motherActivity?.let { WeakReference(it) }

	/**
	 * Builder responsible for creating and managing the options dialog displayed for an active download task.
	 * It is initialized with the current activity context and cleared when the dialog is closed or the
	 * references are destroyed to prevent memory leaks.
	 */
	private var dialogBuilder: DialogBuilder? = DialogBuilder(getSafeActivity())

	/**
	 * The current download task's data model being managed or displayed by this options dialog.
	 * This holds all metadata regarding the specific download, such as its name, URL,
	 * status, and individual settings.
	 */
	private var downloadDataModel: AIODownload? = null

	/**
	 * Safely retrieves the [MotherActivity] from the weak reference.
	 *
	 * @return The [MotherActivity] instance if it is still available in memory and has not
	 * been garbage collected, or null otherwise.
	 */
	private fun getSafeActivity(): MotherActivity? = activityWeakRef?.get()

	/**
	 * Configures and displays the options dialog for a specific active download task.
	 *
	 * This method initializes the dialog with the provided [downloadModel], sets up the
	 * necessary layout views, populates them with file information (thumbnails, titles,
	 * and URLs), and attaches click listeners for various download management actions.
	 *
	 * The dialog will only be shown if the [dialogBuilder], [motherActivity], and
	 * [downloadModel] are all non-null.
	 *
	 * @param downloadModel The data model representing the active download task to be managed.
	 */
	fun show(downloadModel: AIODownload?) {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (downloadModel == null) return

		this@ActiveTasksOptions.downloadDataModel = downloadModel
		dialogBuilder.setView(layout.frag_down_3_active_1_onclick_1)
		setupDialogClickListeners()
		if (dialogBuilder.isShowing == false) {
			dialogBuilder.show()
			updateDialogFileInfo()
			dialogBuilder.dialog.setOnCancelListener { this@ActiveTasksOptions.close() }
			dialogBuilder.dialog.setOnDismissListener { this@ActiveTasksOptions.close() }
		}
	}

	/**
	 * Closes the active options dialog and releases associated resources.
	 *
	 * This method ensures the [DialogBuilder] is dismissed if currently showing
	 * and invokes [clearReferences] to nullify the dialog and activity references
	 * to prevent memory leaks.
	 */
	fun close() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()

		if (dialogBuilder == null) return
		if (activityRef == null) return

		if (dialogBuilder.isShowing) dialogBuilder.close()
		clearReferences()
	}

	/**
	 * Cleans up references to the [DialogBuilder], [AIODownload], and the
	 * [WeakReference] to the activity to prevent potential memory leaks.
	 */
	private fun clearReferences() {
		dialogBuilder = null
		downloadDataModel = null
		activityWeakRef?.clear()
	}

	/**
	 * Updates the visual information displayed within the options dialog.
	 *
	 * This function populates the dialog's layout with data from the [downloadDataModel],
	 * including the file title, source URL, thumbnail image, file type indicators,
	 * media metadata (like duration), and current download preference settings.
	 */
	private fun updateDialogFileInfo() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		val dialogLayout = dialogBuilder.view
		dialogLayout.apply {
			updateFileTitle(dataModel)
			updateFileUrl(dataModel)
			updateThumbnail(dataModel)
			updateFileTypeIndicator(dataModel)
			updatePrivateFolderIndicator(dataModel)
			updateUrlFavicon(dataModel)
			updateMediaPlayIndicator(dataModel)
			updateMediaDuration(dataModel)
			updateDownloadSettingsUI()
		}
	}

	/**
	 * Updates the media duration display for the current download task.
	 *
	 * If the file is a media file (audio or video) and duration metadata is available,
	 * it extracts the duration string, removes any parentheses, and displays it
	 * within the designated duration container. If the file is not a media file,
	 * the duration container is hidden.
	 *
	 * @param dataModel The download data containing file information and media metadata.
	 */
	private fun View.updateMediaDuration(dataModel: AIODownload) {
		val mediaDurContainer = findViewById<View>(R.id.container_media_duration)
		if (isMediaFile(dataModel)) {
			mediaDurContainer.apply {
				val durationTextView = findViewById<TextView>(R.id.txt_media_duration)
				val durationString = dataModel.mediaFilePlaybackDuration
				val formattedDuration = durationString.replace("(", "").replace(")", "")
				if (formattedDuration.isNotEmpty()) {
					showView(this, true)
					showView(durationTextView, true)
					durationTextView.text = formattedDuration
				}
			}
		} else {
			mediaDurContainer.visibility = View.GONE
		}
	}

	/**
	 * Updates the visibility of the media play indicator icon based on whether the file
	 * is a media type (audio or video).
	 *
	 * @param dataModel The [AIODownload] containing the file information to check.
	 */
	private fun View.updateMediaPlayIndicator(dataModel: AIODownload) {
		val playIndicatorIcon = findViewById<ImageView>(R.id.img_media_play_indicator)
		playIndicatorIcon.apply { updatePlayIconVisibility(this, dataModel) }
	}

	/**
	 * Updates the visibility of the play icon based on whether the download is a media file.
	 *
	 * @param imageView The [ImageView] that represents the play indicator.
	 * @param dataModel The [AIODownload] containing file information to check.
	 */
	private fun updatePlayIconVisibility(imageView: ImageView, dataModel: AIODownload) {
		imageView.visibility = (if (isMediaFile(dataModel)) View.VISIBLE else View.GONE)
	}

	/**
	 * Updates the website favicon for the given download data model.
	 *
	 * Locates the favicon [ImageView] within the view and triggers the process
	 * to retrieve and display the site's icon based on the referrer URL.
	 *
	 * @param dataModel The [AIODownload] containing the source site information.
	 */
	private fun View.updateUrlFavicon(dataModel: AIODownload) {
		val siteFavicon = findViewById<ImageView>(R.id.img_site_favicon)
		siteFavicon.apply { updateFaviconInfo(this, dataModel) }
	}

	/**
	 * Asynchronously fetches and displays the website favicon for a download task.
	 *
	 * This function runs on the [IO] dispatcher to perform disk operations. It retrieves
	 * the favicon path associated with the [downloadDataModel]'s referrer site, validates
	 * the file existence, and uses Glide to load the image into the provided [favicon] view.
	 *
	 * If thumbnails are disabled in settings or the favicon cannot be resolved,
	 * a default placeholder is displayed.
	 *
	 * @param favicon The [ImageView] where the favicon should be displayed.
	 * @param downloadDataModel The data model containing the download task details and site referrer.
	 */
	private fun updateFaviconInfo(favicon: ImageView, downloadDataModel: AIODownload) {
		getSafeActivity()?.getAttachedCoroutineScope()?.launch(IO) {
			val defaultFaviconResId = R.drawable.ic_image_default_favicon
			val defaultFaviconDrawable = getDrawable(INSTANCE.resources, defaultFaviconResId, null)

			if (isVideoThumbnailNotAllowed(downloadDataModel)) {
				withContext(Main) { favicon.setImageDrawable(defaultFaviconDrawable) }
				return@launch
			}

			try {
				val referralSite = downloadDataModel.siteReferrer
				aioFavicons.getFavicon(referralSite)?.let { faviconFilePath ->
					val faviconImgFile = File(faviconFilePath)
					if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
						return@launch
					}
					val faviconImgURI = faviconImgFile.toUri()
					val signature = ObjectKey(File(faviconFilePath).lastModified())

					withContext(Main) {
						try {
							showView(favicon, true)
							Glide.with(favicon)
								.load(faviconImgURI)
								.placeholder(defaultFaviconDrawable)
								.signature(signature)
								.into(favicon)
						} catch (error: Exception) {
							error.printStackTrace()
							showView(favicon, true)
							favicon.setImageResource(defaultFaviconResId)
						}
					}
				}
			} catch (error: Exception) {
				withContext(Main) {
					favicon.setImageDrawable(defaultFaviconDrawable)
				}
			}
		}
	}

	/**
	 * Updates the visibility and icon of the private folder indicator for the download item.
	 *
	 * This function identifies whether the download is being saved to a private/locked
	 * location or a standard folder based on the [dataModel] settings and updates
	 * the corresponding [ImageView] accordingly.
	 *
	 * @param dataModel The data model containing download details and destination settings.
	 */
	private fun View.updatePrivateFolderIndicator(dataModel: AIODownload) {
		val folderIndicatorView = findViewById<ImageView>(R.id.img_private_folder_indicator)
		folderIndicatorView.apply { setDownloadDestinationIcon(this, dataModel) }
	}

	/**
	 * Sets the appropriate icon for the download destination [imageView] based on the
	 * storage location defined in the [dataModel].
	 *
	 * It displays a lock icon if the destination is set to the [PRIVATE_FOLDER],
	 * otherwise it defaults to a standard folder icon.
	 *
	 * @param imageView The [ImageView] where the destination icon will be displayed.
	 * @param dataModel The [AIODownload] containing the global settings and download location info.
	 */
	private fun setDownloadDestinationIcon(imageView: ImageView, dataModel: AIODownload) {
		val globalSettings = dataModel.config
		val downloadLocation = globalSettings.defaultDownloadLocationType

		imageView.setImageResource(
			when (downloadLocation) {
				PRIVATE_FOLDER -> R.drawable.ic_button_lock
				else -> R.drawable.ic_button_folder
			}
		)
	}

	/**
	 * Updates the file type icon displayed in the download options dialog.
	 *
	 * This function finds the [ImageView] associated with the file type indicator and
	 * invokes [setFileTypeIcon] to determine and set the appropriate resource based
	 * on the [dataModel]'s file extension or category.
	 *
	 * @param dataModel The [AIODownload] containing the file information used to determine the icon.
	 */
	private fun View.updateFileTypeIndicator(dataModel: AIODownload) {
		val fileTypeIcon = findViewById<ImageView>(R.id.img_file_type_indicator)
		fileTypeIcon.apply { setFileTypeIcon(this, dataModel) }
	}

	/**
	 * Sets the appropriate file type icon to the provided [ImageView] based on the
	 * extension or category of the file name in the [dataModel].
	 *
	 * @param imageView The [ImageView] where the resource icon will be set.
	 * @param dataModel The [AIODownload] containing the file name information.
	 */
	private fun setFileTypeIcon(imageView: ImageView, dataModel: AIODownload) {
		imageView.setImageResource(
			when {
				isImageByName(dataModel.fileName) -> R.drawable.ic_button_images
				isAudioByName(dataModel.fileName) -> R.drawable.ic_button_audio
				isVideoByName(dataModel.fileName) -> R.drawable.ic_button_video
				isDocumentByName(dataModel.fileName) -> R.drawable.ic_button_document
				isArchiveByName(dataModel.fileName) -> R.drawable.ic_button_archives
				isProgramByName(dataModel.fileName) -> R.drawable.ic_button_programs
				else -> R.drawable.ic_button_file
			}
		)
	}

	/**
	 * Updates the thumbnail image view for the given [dataModel].
	 *
	 * It finds the [ImageView] associated with the file thumbnail and triggers
	 * the [loadThumbnail] process to populate it.
	 *
	 * @param dataModel The download data containing the information needed to load the thumbnail.
	 */
	private fun View.updateThumbnail(dataModel: AIODownload) {
		val thumbnailImageView = findViewById<ImageView>(R.id.img_file_thumbnail)
		thumbnailImageView.apply { loadThumbnail(this, dataModel) }
	}

	/**
	 * Updates the UI label with the source URL of the file from the provided [dataModel].
	 *
	 * @param dataModel The data model containing the file's download URL.
	 */
	private fun View.updateFileUrl(dataModel: AIODownload) {
		val fileUrlLabel = findViewById<TextView>(R.id.txt_file_url)
		fileUrlLabel.apply { text = dataModel.fileURL }
	}

	/**
	 * Updates the file title [TextView] within the dialog layout.
	 *
	 * This function sets the text to the filename from the [dataModel], enables
	 * horizontal scrolling (marquee) by setting `isSelected = true`, and
	 * normalizes any tall symbols to ensure consistent line height.
	 *
	 * @param dataModel The data model containing the filename to display.
	 */
	private fun View.updateFileTitle(dataModel: AIODownload) {
		val fileTitleLabel = findViewById<TextView>(R.id.txt_file_title)
		fileTitleLabel.apply { isSelected = true; text = dataModel.fileName }
		fileTitleLabel.normalizeTallSymbols()
	}

	/**
	 * Determines if the given [downloadModel] represents a media file (audio or video)
	 * based on its file name extension.
	 *
	 * @param downloadModel The download data containing the file name to check.
	 * @return `true` if the file is identified as audio or video, `false` otherwise.
	 */
	private fun isMediaFile(downloadModel: AIODownload): Boolean =
		isAudioByName(downloadModel.fileName) || isVideoByName(downloadModel.fileName)

	/**
	 * Checks whether the display of a video thumbnail is restricted based on the
	 * file type and the user's global privacy/display settings.
	 *
	 * @param downloadDataModel The data model containing the download information and global settings.
	 * @return `true` if the file is a video and the "hide video thumbnail" setting is enabled; `false` otherwise.
	 */
	private fun isVideoThumbnailNotAllowed(downloadDataModel: AIODownload): Boolean {
		val isVideoHidden = downloadDataModel.config.downloadHideVideoThumbnail
		return isVideo(downloadDataModel.getDestinationDocumentFile()) && isVideoHidden
	}

	/**
	 * Loads the thumbnail for a download task into the provided [ImageView].
	 *
	 * The function follows a prioritized loading logic:
	 * 1. Checks if video thumbnails are hidden via global settings; if so, loads a default icon.
	 * 2. Attempts to extract and load an icon if the file is an APK.
	 * 3. Attempts to load a previously cached thumbnail from [AIODownload.thumbPath] using Glide.
	 * 4. If no cache exists, it generates a new thumbnail from the local [destinationFile] or [thumbnailUrl].
	 * 5. Handles orientation by rotating portrait thumbnails.
	 * 6. Saves the generated bitmap to a file, updates the data model, and caches it for future use.
	 *
	 * @param imageView The target view where the thumbnail will be displayed.
	 * @param dataModel The data model containing file metadata, settings, and thumbnail paths.
	 */
	private fun loadThumbnail(imageView: ImageView, dataModel: AIODownload) {
		getSafeActivity()?.getAttachedCoroutineScope()?.launch(IO) {
			val destinationFile = dataModel.getDestinationFile()
			val defaultThumb = dataModel.getThumbnailDrawableID()
			val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

			if (dataModel.config.downloadHideVideoThumbnail) {
				imageView.setImageResource(defaultThumb)
				return@launch
			}

			var shouldReturn = false
			withContext(Main) {
				if (loadApkThumbnail(dataModel, imageView, defaultThumbDrawable)) {
					shouldReturn = true
				}
			}

			if (shouldReturn) return@launch

			val cachedThumbPath = dataModel.thumbPath
			if (cachedThumbPath.isNotEmpty()) {
				withContext(Main) {
					loadBitmapWithGlide(
						imageView = imageView,
						filePath = dataModel.thumbPath,
						defaultThumb = defaultThumb
					)
				}
				return@launch
			}

			val thumbnailUrl = dataModel.videoInfo?.videoThumbnailUrl
			val bitmap = getThumbnailFromFile(destinationFile, thumbnailUrl, 420)

			if (bitmap != null) {
				val isPortrait = bitmap.height > bitmap.width
				val rotatedBitmap = if (isPortrait) {
					rotateBitmap(bitmap = bitmap, angle = 270f)
				} else bitmap

				val thumbnailName = "${dataModel.taskId}$THUMB_EXTENSION"
				saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
					dataModel.thumbPath = filePath
					dataModel.updateInDB()
					withContext(Main) {
						loadBitmapWithGlide(
							imageView = imageView,
							filePath = dataModel.thumbPath,
							defaultThumb = defaultThumb
						)
					}
				}
			}
		}
	}

	/**
	 * Loads an image from the local file system into a specified [ImageView] using the Glide library.
	 *
	 * This method utilizes the file's last modified timestamp as a [signature] to handle
	 * cache invalidation, ensuring that if the thumbnail file is updated, Glide will
	 * refresh the view instead of showing a cached version.
	 *
	 * @param imageView The target view where the bitmap should be displayed.
	 * @param filePath The absolute path to the image file on the local storage.
	 * @param defaultThumb The resource ID of a drawable to be used as a placeholder
	 * while loading or as a fallback if the loading fails.
	 */
	private fun loadBitmapWithGlide(imageView: ImageView, filePath: String, defaultThumb: Int) {
		try {
			val imageFile = File(filePath)
			val signature = ObjectKey(imageFile.lastModified())
			Glide.with(imageView)
				.load(imageFile)
				.placeholder(defaultThumb)
				.signature(signature)
				.into(imageView)
		} catch (error: Exception) {
			imageView.setImageResource(defaultThumb)
		}
	}

	/**
	 * Attempts to extract and load the application icon from an APK file to the provided [ImageView].
	 *
	 * This method checks if the file exists and has an `.apk` extension. It uses [PackageManager.getPackageArchiveInfo]
	 * to retrieve the package details. To ensure the icon is loaded correctly, it manually assigns the
	 * [apkFile] path to the `sourceDir` and `publicSourceDir` of the application info before calling `loadIcon`.
	 *
	 * @param dataModel The [AIODownload] containing file information and destination path.
	 * @param imageView The [ImageView] where the extracted icon should be displayed.
	 * @param fallbackDrawable An optional [Drawable] to display if the APK is invalid or extraction fails.
	 * @return `true` if the icon was successfully loaded; `false` otherwise.
	 */
	private fun loadApkThumbnail(
		dataModel: AIODownload,
		imageView: ImageView, fallbackDrawable: Drawable?
	): Boolean {
		getSafeActivity()?.let { activityRef ->
			val apkFile = dataModel.getDestinationFile()

			if (!apkFile.exists() || !apkFile.name.lowercase().endsWith(".apk")) {
				imageView.setImageDrawable(fallbackDrawable)
				return false
			}

			val packageManager: PackageManager = activityRef.packageManager
			return try {
				val packageInfo: PackageInfo? = packageManager.getPackageArchiveInfo(
					apkFile.absolutePath,
					PackageManager.GET_ACTIVITIES
				)

				packageInfo?.applicationInfo?.let { appInfo ->
					appInfo.sourceDir = apkFile.absolutePath
					appInfo.publicSourceDir = apkFile.absolutePath

					val icon: Drawable = appInfo.loadIcon(packageManager)
					imageView.setImageDrawable(icon)
					true
				} ?: run {
					false
				}
			} catch (error: Exception) {
				imageView.apply {
					scaleType = ImageView.ScaleType.FIT_CENTER
					setPadding(0, 0, 0, 0)
					setImageDrawable(fallbackDrawable)
				}
				false
			}
		}
		return false
	}

	/**
	 * Attempts to resume a paused or interrupted download task.
	 *
	 * This function performs the following steps:
	 * 1. Validates the availability of the dialog builder, activity, and data model.
	 * 2. Closes the current options dialog.
	 * 3. Checks if the task is already active; if so, it pauses it briefly before attempting to resume.
	 * 4. Checks for `yt-dlp` specific issues or authentication requirements:
	 *    - If a login is required (e.g., for private videos), it displays a message dialog directing
	 *      the user to log in via the internal browser.
	 *    - If no critical errors exist, it invokes the [downloadSystem] to resume the download
	 *      on an IO coroutine and shows a success toast upon resumption.
	 */
	private fun resumeDownloadTask() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		this@ActiveTasksOptions.close()
		activityRef.getAttachedCoroutineScope().launch(Main) {
			if (searchActiveTaskByDownloadModel(dataModel) != null) {
				pauseDownloadTask()
				delay(1000)
			}

			val isDownloadProblematic = dataModel.isYtdlpErrorFound &&
				dataModel.ytdlpErrorMessage.isNotEmpty() &&
				dataModel.downloadStatus != DOWNLOADING

			val requiresLogin = dataModel.ytdlpErrorMessage.contains("login", true)

			if (!isDownloadProblematic && !requiresLogin) {
				downloadSystem.resumeDownload(
					downloadModel = dataModel,
					coroutineScope = CoroutineScope(IO),
					onResumed = {
						val toastMsgId = string.title_resumed_task_successfully
						showToast(activityRef, toastMsgId)
					}
				)
				return@launch
			}

			if (hasRecentLoginSessionForHost(getHostFromUrl(dataModel.siteReferrer))) {
				downloadSystem.resumeDownload(
					downloadModel = dataModel,
					coroutineScope = CoroutineScope(IO),
					onResumed = {
						val toastMsgId = string.title_resumed_task_successfully
						showToast(activityRef, toastMsgId)
					}
				)
				return@launch
			} else {
				LoginRequiredDialog(activityRef, dataModel).show()
			}
		}
	}

	/**
	 * Pauses the currently selected download task.
	 *
	 * This method performs the following safety checks and actions:
	 * 1. Validates the existence of the dialog, activity context, and data model.
	 * 2. Closes the options menu.
	 * 3. Checks if the task is still active; if not, displays a "already paused" toast.
	 * 4. If the download source does not support resuming, it displays a warning dialog
	 *    asking for user confirmation before pausing.
	 * 5. If resume is supported or the user confirms the warning, it triggers the
	 *    underlying download system to pause the task.
	 */
	private fun pauseDownloadTask() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		this@ActiveTasksOptions.close()
		activityRef.getAttachedCoroutineScope().launch(Main) {
			if (searchActiveTaskByDownloadModel(dataModel) == null) {
				val msgId = string.title_download_task_already_paused
				showToast(activityRef, msgId)
				return@launch
			}

			if (!dataModel.isResumeSupported) {
				getMessageDialog(
					baseActivityInf = activityRef,
					isNegativeButtonVisible = false,
					messageTextViewCustomize = {
						val warningTextResId = string.text_warning_resume_not_supported
						it.setText(warningTextResId)
					},
					negativeButtonTextCustomize = {
						val drawableResId = R.drawable.ic_button_cancel
						it.setLeftSideDrawable(drawableResId)
					},
					positiveButtonTextCustomize = {
						it.setText(string.title_pause_anyway)
						it.setLeftSideDrawable(R.drawable.ic_button_media_pause)
					}
				)?.apply {
					setOnClickForPositiveButton {
						this.close()
						this@ActiveTasksOptions.close()
						downloadSystem.pauseDownload(dataModel)
					}
				}?.show()
			} else {
				downloadSystem.pauseDownload(dataModel)
			}
		}
	}

	/**
	 * Attempts to remove the download task from the list.
	 *
	 * If the task is currently active (downloading), the function will first attempt to
	 * pause it and wait for a brief period. If it cannot be paused or remains active,
	 * an error dialog is shown to the user.
	 *
	 * If the task is successfully paused or was already inactive, a confirmation dialog
	 * is displayed. Upon user confirmation, the task is cleared from the download system
	 * and a success toast is shown.
	 */
	private fun removeDownloadTask() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		activityRef.getAttachedCoroutineScope().launch(Main) {
			if (searchActiveTaskByDownloadModel(dataModel) != null) {
				pauseDownloadTask()
				delay(1500)
			}

			if (searchActiveTaskByDownloadModel(dataModel) != null) {
				showMessageDialog(
					baseActivityInf = activityRef,
					isNegativeButtonVisible = false,
					messageTextViewCustomize = { it.setText(string.text_cant_remove_one_active_download) },
					positiveButtonTextCustomize = { it.setLeftSideDrawable(R.drawable.ic_button_checked_circle) }
				)
				return@launch
			}

			getMessageDialog(
				baseActivityInf = activityRef,
				isTitleVisible = true,
				isNegativeButtonVisible = false,
				titleTextViewCustomize = { it.setText(string.title_are_you_sure_about_this) },
				messageTextViewCustomize = { it.setText(string.text_are_you_sure_about_clear) },
				positiveButtonTextCustomize = {
					it.setText(string.title_clear_from_list)
					it.setLeftSideDrawable(R.drawable.ic_button_clear)
				}
			)?.apply {
				setOnClickForPositiveButton {
					this.close()
					this@ActiveTasksOptions.close()
					dataModel.let {
						downloadSystem.clearDownload(it) {
							showToast(activityRef, string.title_successfully_cleared)
						}
					}
				}
			}?.show()
		}
	}

	/**
	 * Searches for an active download task associated with the provided [AIODownload].
	 *
	 * This function queries the [downloadSystem] on an IO dispatcher to determine if the
	 * specified download is currently being processed in the active task queue.
	 *
	 * @param dataModel The data model representing the download to search for.
	 * @return The corresponding [DownloadTaskInf] if an active task is found, or `null` otherwise.
	 */
	private suspend fun searchActiveTaskByDownloadModel(dataModel: AIODownload): DownloadTaskInf? {
		return withContext(IO) { downloadSystem.searchActiveDownloadTaskWith(dataModel) }
	}

	/**
	 * Initiates the deletion process for a download task and its associated file.
	 *
	 * This function performs the following steps:
	 * 1. Validates that the dialog builder, activity, and data model are available.
	 * 2. Checks if the task is currently active; if so, it attempts to pause it and waits
	 *    for the state to update.
	 * 3. Displays an error dialog if the task cannot be stopped (remaining active).
	 * 4. Shows a confirmation dialog asking the user if they are sure about deleting the file.
	 * 5. Upon confirmation, triggers the download system to delete the file and the record,
	 *    then notifies the user with a success toast.
	 */
	private fun deleteDownloadTask() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		activityRef.getAttachedCoroutineScope().launch(Main) {
			if (downloadSystem.searchActiveDownloadTaskWith(dataModel) != null) {
				pauseDownloadTask()
				delay(1500)
			}

			if (downloadSystem.searchActiveDownloadTaskWith(dataModel) != null) {
				showMessageDialog(
					baseActivityInf = activityRef,
					isNegativeButtonVisible = false,
					messageTextViewCustomize = { it.setText(string.text_cant_delete_on_active_download) },
					positiveButtonTextCustomize = { it.setLeftSideDrawable(R.drawable.ic_button_checked_circle) }
				)
				return@launch
			}
		}

		getMessageDialog(
			baseActivityInf = activityRef,
			isTitleVisible = true,
			isNegativeButtonVisible = false,
			titleTextViewCustomize = { it.setText(string.title_are_you_sure_about_this) },
			messageTextViewCustomize = { it.setText(string.text_are_you_sure_about_delete) },
			positiveButtonTextCustomize = {
				it.setText(string.title_delete_file)
				it.setLeftSideDrawable(R.drawable.ic_button_checked_circle)
			}
		)?.apply {
			setOnClickForPositiveButton {
				this.close()
				this@ActiveTasksOptions.close()

				dataModel.let {
					downloadSystem.deleteDownload(it) {
						showToast(activityRef, string.title_successfully_deleted)
					}
				}
			}
		}?.show()
	}

	/**
	 * Initiates the file renaming process for the current download task.
	 *
	 * This function performs a safety check to ensure the task is not currently active.
	 * If the task is active, it displays a warning dialog to the user. Otherwise,
	 * it launches the [DownloadFileRenamer] dialog to allow the user to input a new name.
	 */
	private fun renameDownloadTask() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		activityRef.getAttachedCoroutineScope().launch(Main) {
			if (downloadSystem.searchActiveDownloadTaskWith(dataModel) != null) {
				showMessageDialog(
					baseActivityInf = activityRef,
					isNegativeButtonVisible = false,
					messageTextViewCustomize = { it.setText(string.text_cant_rename_on_active_download) },
					positiveButtonTextCustomize = { it.setLeftSideDrawable(R.drawable.ic_button_checked_circle) }
				)
				return@launch
			}
			val downloadFileRenamer = DownloadFileRenamer(activityRef, dataModel) { this@ActiveTasksOptions.close() }
			downloadFileRenamer.show(dataModel)
		}
	}

	/**
	 * Toggles the visibility of the video thumbnail for the current download task.
	 *
	 * This method flips the [AIODownload.config.downloadHideVideoThumbnail] state,
	 * persists the change to storage, and refreshes both the active task UI and the
	 * current options dialog to reflect the updated visibility.
	 */
	private fun toggleDownloadThumbnail() {
		val dataModel = downloadDataModel
		getSafeActivity() ?: return
		if (dataModel == null) return

		val settings = dataModel.config
		val shouldHideVideoThumbnail = settings.downloadHideVideoThumbnail
		settings.downloadHideVideoThumbnail = !shouldHideVideoThumbnail
		dataModel.updateInDB()
		downloadSystem.downloadsUIManager.updateActiveUI(dataModel)
		updateDownloadSettingsUI()
		dialogBuilder?.view?.apply {
			updateFileTitle(dataModel)
			updateFileUrl(dataModel)
			updateThumbnail(dataModel)
			updateFileTypeIndicator(dataModel)
			updatePrivateFolderIndicator(dataModel)
			updateUrlFavicon(dataModel)
			updateMediaPlayIndicator(dataModel)
			updateMediaDuration(dataModel)
		}
	}

	/**
	 * Copies the direct download URL of the current file to the system clipboard.
	 *
	 * This function validates the URL from the [downloadDataModel]. If the URL is valid,
	 * it copies the text to the clipboard, displays a success toast, and closes the options dialog.
	 * If the URL is invalid or missing, it informs the user via a toast message.
	 */
	private fun copyDownloadFileLink() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		dataModel.fileURL.takeIf { isValidURL(it) }?.let { fileUrl ->
			copyTextToClipboard(activityRef, fileUrl)
			showToast(activityRef, string.title_file_url_has_been_copied)
			close()
		} ?: run {
			showToast(activityRef, string.title_dont_have_anything_to_copy)
		}
	}

	/**
	 * Copies the website referrer URL (the source page where the download originated)
	 * to the system clipboard.
	 *
	 * This method validates the existence of the dialog, activity, and data model.
	 * If the referrer URL is valid, it copies the text and shows a success toast;
	 * otherwise, it displays a toast indicating there is nothing to copy.
	 */
	private fun copyWebsiteLink() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		dataModel.siteReferrer.takeIf { isValidURL(it) }?.let { fileUrl ->
			copyTextToClipboard(activityRef, fileUrl)
			showToast(activityRef, string.title_file_url_has_been_copied)
			close()
		} ?: run {
			showToast(activityRef, string.title_dont_have_anything_to_copy)
		}
	}

	/**
	 * Shares the source URL of the current download task using the system's share sheet.
	 *
	 * This method validates the file URL from the [downloadDataModel]. If a valid URL is found,
	 * it triggers the [shareUrl] utility and closes the current options dialog. If the URL
	 * is missing or invalid, a toast message is displayed to the user.
	 */
	private fun shareDownloadFileLink() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		dataModel.fileURL.takeIf { isValidURL(it) }?.let { fileUrl ->
			val titleText = getText(string.title_share_download_file_url)
			shareUrl(activityRef, fileUrl, titleText) { close() }
		} ?: run {
			showToast(activityRef, string.title_dont_have_anything_to_share)
		}
	}

	/**
	 * Opens the website from which the download was initiated (the referrer link).
	 *
	 * If a valid referrer link exists, it closes the current options dialog,
	 * creates a new browser tab with that URL, and navigates the user to the
	 * browser fragment. If no link is found, it triggers a vibration and
	 * shows a warning toast.
	 */
	private fun openDownloadReferrerLink() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		val downloadSiteReferrerLink = dataModel.siteReferrer
		if (downloadSiteReferrerLink.isEmpty()) {
			activityRef.doSomeVibration()
			showToast(activityRef, string.title_no_referer_link_found)
			return
		}

		this@ActiveTasksOptions.close()
		val webviewEngine = activityRef.browserFragment?.browserFragmentBody?.webviewEngine!!
		activityRef.sideNavigation?.addNewBrowsingTab(downloadSiteReferrerLink, webviewEngine)
		activityRef.openBrowserFragment()
	}

	/**
	 * Opens a dialog to configure and apply speed limits for the current download task.
	 *
	 * This method allows the user to restrict the bandwidth usage of a specific download
	 * by interacting with the download system's speed limiter settings.
	 */
	private fun openDownloadSpeedLimiter() {
		try {
			if (downloadDataModel == null) return
			if (getSafeActivity() == null) return

			DownloadSpeedLimiter(getSafeActivity(), downloadDataModel!!) {
				showToast(getSafeActivity(), string.title_setting_applied)
				close()
			}.showDialog()
		} catch (error: Exception) {
			logger.e("Error while showing download speed limiter dialog", error)
		}
	}

	/**
	 * Opens a detailed tracking dialog for the current download task.
	 *
	 * This method initializes a [DownloadInfoTracker] to display real-time technical
	 * information and logs regarding the [downloadDataModel]. Upon successfully
	 * launching the tracker, the current options menu is closed.
	 */
	private fun openDownloadInfoTracker() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		DownloadInfoTracker(activityRef).show(dataModel)
		this@ActiveTasksOptions.close()
	}

	/**
	 * Closes the current options dialog and opens the [VideoLinkPasteEditor] to allow the user
	 * to pick a different resolution or format for the current video.
	 *
	 * It uses the [AIODownload.fileURL] or [AIODownload.siteReferrer] as the source
	 * link to re-parse the available media options.
	 */
	private fun downloadOtherYTResolutions() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity() ?: return
		if (dataModel == null) return

		close()
		val fileUrl = dataModel.fileURL.ifEmpty { dataModel.siteReferrer }
		if (fileUrl.isEmpty()) return
		VideoLinkPasteEditor(
			baseActivity = activityRef,
			passOnUrl = fileUrl,
			autoStart = true
		).show()
	}

	/**
	 * Toggles the setting that restricts downloading to Wi-Fi connections only.
	 *
	 * This method flips the current [AIODownload.config.downloadWifiOnly] state,
	 * persists the change to storage, and refreshes the options dialog UI to reflect
	 * the new setting.
	 */
	private fun toggleWifiOnlyDownload() {
		val dataModel = downloadDataModel
		getSafeActivity() ?: return
		if (dataModel == null) return

		val settings = dataModel.config
		val isDownloadWifiOnly = settings.downloadWifiOnly
		settings.downloadWifiOnly = !isDownloadWifiOnly
		dataModel.updateInDB()
		updateDownloadSettingsUI()
	}

	/**
	 * Toggles the visibility of the download progress notification for the current task.
	 *
	 * This method flips the [AIODownload.config] property `downloadHideNotification`,
	 * persists the change to storage, and refreshes the dialog UI to reflect the new state.
	 */
	private fun toggleDownloadNotification() {
		val dataModel = downloadDataModel
		getSafeActivity() ?: return
		if (dataModel == null) return

		val settings = dataModel.config
		val isDownloadWifiOnly = settings.downloadHideNotification
		settings.downloadHideNotification = !isDownloadWifiOnly
		dataModel.updateInDB()
		updateDownloadSettingsUI()
	}

	/**
	 * Toggles the setting for playing a notification sound when a download completes.
	 *
	 * This function reverses the current [AIODownload.config.downloadPlayNotificationSound]
	 * boolean value, persists the change to storage, and refreshes the options dialog UI
	 * to reflect the new state.
	 */
	private fun toggleDownloadSound() {
		val dataModel = downloadDataModel
		getSafeActivity() ?: return
		if (dataModel == null) return

		val settings = dataModel.config
		val isDownloadWifiOnly = settings.downloadPlayNotificationSound
		settings.downloadPlayNotificationSound = !isDownloadWifiOnly
		dataModel.updateInDB()
		updateDownloadSettingsUI()
	}

	/**
	 * Updates the visual state of the toggle switches (checkbox icons) within the options dialog.
	 *
	 * This function synchronizes the UI components with the current [AIODownload] settings,
	 * specifically for:
	 * - Wi-Fi only downloading preference.
	 * - Download notification visibility.
	 * - Completion sound alerts.
	 * - Video thumbnail display preferences.
	 *
	 * It ensures that the right-side drawables of the respective [TextView] buttons reflect
	 * the active/inactive state of each setting.
	 */
	private fun updateDownloadSettingsUI() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		val settings = dataModel.config
		dialogBuilder.view.findViewById<TextView>(R.id.txt_wifi_only_download).apply {
			val drawableResIdRes = if (settings.downloadWifiOnly) {
				R.drawable.ic_button_checked_circle_small
			} else R.drawable.ic_button_unchecked_circle_small
			setRightSideDrawable(drawableResIdRes, true)
		}

		dialogBuilder.view.findViewById<TextView>(R.id.txt_download_notification).apply {
			val drawableResIdRes = if (settings.downloadHideNotification) {
				R.drawable.ic_button_unchecked_circle_small
			} else R.drawable.ic_button_checked_circle_small
			setRightSideDrawable(drawableResIdRes, true)
		}

		dialogBuilder.view.findViewById<TextView>(R.id.txt_play_download_sound).apply {
			val drawableResIdRes = if (settings.downloadPlayNotificationSound) {
				R.drawable.ic_button_checked_circle_small
			} else R.drawable.ic_button_unchecked_circle_small
			setRightSideDrawable(drawableResIdRes, true)
		}

		dialogBuilder.view.findViewById<TextView>(R.id.txt_remove_thumbnail).apply {
			val drawableResIdRes = if (settings.downloadHideVideoThumbnail) {
				R.drawable.ic_button_unchecked_circle_small
			} else R.drawable.ic_button_checked_circle_small
			setRightSideDrawable(drawableResIdRes, true)
		}
	}

	/**
	 * Opens the User-Agent configuration setting for the current download task.
	 *
	 * This method allows the user to view or modify the User-Agent string associated with
	 * the specific download request, which can be useful for bypassing server-side
	 * restrictions or mimicking specific browsers.
	 */
	private fun openUserAgentSetting() {
		try {
			getSafeActivity()?.let { activity ->
				downloadDataModel?.let { model ->
					HttpUserAgentSelector(activity, model).apply {
						onApplyListener = { updatedModel ->
							updatedModel.updateInDB()
							showToast(activity, string.title_setting_applied)
							close()
						}
					}.show()
				}
			}
		} catch (error: Exception) {
			logger.e("Error while showing user agent dialog", error)
		}
	}

	/**
	 * Configures click listeners for all interactive elements within the options dialog.
	 *
	 * This method maps various view IDs (buttons and cards) to their respective actions,
	 * such as resuming/pausing downloads, renaming files, toggling settings (WiFi-only,
	 * notifications, sounds), and copying or sharing links.
	 *
	 * The listeners are applied to the view held by [dialogBuilder] only if the dialog,
	 * the activity context, and the associated [downloadDataModel] are all non-null.
	 */
	private fun setupDialogClickListeners() {
		val dialogBuilder = dialogBuilder
		val activityRef = getSafeActivity()
		val dataModel = downloadDataModel

		if (dialogBuilder == null) return
		if (activityRef == null) return
		if (dataModel == null) return

		dialogBuilder.view.apply {
			val clickActions = mapOf(
				R.id.btn_file_info_card to { openDownloadReferrerLink() },
				R.id.btn_resume_download to { resumeDownloadTask() },
				R.id.btn_pause_download to { pauseDownloadTask() },
				R.id.btn_clear_download to { removeDownloadTask() },
				R.id.btn_delete_download to { deleteDownloadTask() },
				R.id.btn_rename_download to { renameDownloadTask() },
				R.id.btn_remove_thumbnail to { toggleDownloadThumbnail() },
				R.id.btn_copy_site_link to { copyWebsiteLink() },
				R.id.btn_copy_download_url to { copyDownloadFileLink() },
				R.id.btn_share_download_url to { shareDownloadFileLink() },
				R.id.btn_open_website to { openDownloadReferrerLink() },
				R.id.btn_speed_limiter to { openDownloadSpeedLimiter() },
				R.id.btn_wifi_only_download to { toggleWifiOnlyDownload() },
				R.id.btn_download_notification to { toggleDownloadNotification() },
				R.id.btn_play_download_sound to { toggleDownloadSound() },
				R.id.btn_download_user_agent to { openUserAgentSetting() },
				R.id.btn_download_other_resolution to { downloadOtherYTResolutions() },
				R.id.btn_download_system_information to { openDownloadInfoTracker() }
			)
			clickActions.forEach { (viewId, action) ->
				findViewById<View>(viewId).setOnClickListener {
					action()
				}
			}
		}
	}
}