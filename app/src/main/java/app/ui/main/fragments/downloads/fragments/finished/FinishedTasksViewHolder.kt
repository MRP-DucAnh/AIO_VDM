package app.ui.main.fragments.downloads.fragments.finished

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.BaseActivity
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.downloader.DownloadDataModel.Companion.THUMB_EXTENSION
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import com.aio.R
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.DateTimeUtils.formatLastModifiedDate
import lib.files.FileSizeFormatter.humanReadableSizeOf
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isVideoByName
import lib.networks.DownloaderUtils.getAudioPlaybackTimeIfAvailable
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.fromHtmlStringToSpanned
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.showView
import java.io.File

class FinishedTasksViewHolder(val layout: View) {

	private val logger = LogHelperUtils.from(javaClass)

	private val detailsCache = object : LruCache<String, Spanned>(100) {}
	private var currentCoroutineJob: Job? = null
	private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val rootConLayout: RelativeLayout by lazy { layout.findViewById(R.id.button_finish_download_row) }
	private val thumbImgView: ImageView by lazy { layout.findViewById(R.id.img_file_thumbnail) }
	private val faviconImgView: ImageView by lazy { layout.findViewById(R.id.img_site_favicon) }
	private val titleTxtView: TextView by lazy { layout.findViewById(R.id.txt_file_name) }
	private val metadataTxtView: TextView by lazy { layout.findViewById(R.id.txt_file_info) }
	private val durationTxtView: TextView by lazy { layout.findViewById(R.id.txt_media_duration) }
	private val durationConLayout: View by lazy { layout.findViewById(R.id.container_media_duration) }
	private val playIndicatorView: View by lazy { layout.findViewById(R.id.img_media_play_indicator) }
	private val fileTypeImgView: ImageView by lazy { layout.findViewById(R.id.img_file_type_indicator) }
	private val openFileIndicatorImgView: ImageView by lazy { layout.findViewById(R.id.btn_open_download_file) }
	private val privateFolderImgView: ImageView by lazy { layout.findViewById(R.id.img_private_folder_indicator) }

	fun updateView(
		dataModel: DownloadDataModel?,
		eventListener: FinishedTasksClickEvents?
	) {
		if (dataModel == null) return
		if (eventListener == null) return
		logger.d("updateView: Starting for download ${dataModel.downloadId}")
		clearResources()
		currentCoroutineJob = coroutineScope.launch {
			logger.d("updateView: Coroutine started for ${dataModel.downloadId}")
			refreshDownloadProgress(dataModel)
			setupItemClickEventListeners(eventListener, dataModel)
		}
	}

	fun clearResources() {
		logger.d("clearResources: Cleaning up ViewHolder resources")
		try {
			currentCoroutineJob?.cancel()
			coroutineScope.coroutineContext.cancelChildren()
			detailsCache.evictAll()

			if (rootConLayout.context is BaseActivity) {
				val activity = rootConLayout.context as BaseActivity
				if (activity.isDestroyed || !activity.isActivityRunning()) {
					logger.d("clearResources: Activity not running, skipping Glide cleanup")
					return
				}
			}

			Glide.with(thumbImgView).clear(thumbImgView)
			Glide.with(faviconImgView).clear(faviconImgView)

			thumbImgView.setImageDrawable(null)
			faviconImgView.setImageDrawable(null)
			rootConLayout.setOnClickListener(null)
			rootConLayout.setOnLongClickListener(null)
			rootConLayout.isClickable = false
			layout.tag = null
			logger.d("clearResources: Cleanup completed successfully")
		} catch (error: Exception) {
			logger.e("clearResources: Error during cleanup - ${error.message}", error)
		}
	}

	fun cancelAll() {
		logger.d("cancelAll: Cancelling entire coroutine scope")
		coroutineScope.cancel()
	}

	private suspend fun setupItemClickEventListeners(
		eventListener: FinishedTasksClickEvents,
		dataModel: DownloadDataModel
	) {
		withContext(Dispatchers.Main) {
			if (!isActive) {
				logger.d("setupItemClickEventListeners: Coroutine not active")
				return@withContext
			}
			logger.d("setupItemClickEventListeners: Setting up listeners for ${dataModel.downloadId}")
			rootConLayout.apply {
				isClickable = true
				setOnClickListener {
					logger.d("ClickListener: Download ${dataModel.downloadId} clicked")
					eventListener.onFinishedDownloadClick(dataModel)
				}
				setOnLongClickListener {
					logger.d("LongClickListener: Download ${dataModel.downloadId} long-clicked")
					eventListener.onFinishedDownloadLongClick(dataModel); true
				}
			}
		}
	}

	private suspend fun refreshDownloadProgress(dataModel: DownloadDataModel) {
		logger.d("refreshDownloadProgress: Starting UI refresh for ${dataModel.downloadId}")
		updateFilesTitle(dataModel)
		updateFilesMetaInfo(dataModel)
		updateFaviconInfo(dataModel)
		updateThumbnailInfo(dataModel)
		updateFileTypeIndicator(dataModel)
		updatePrivateFolderIndicator(dataModel)
		updateOpenFileIndicator(dataModel)
		logger.d("refreshDownloadProgress: Completed UI refresh for ${dataModel.downloadId}")
	}

	private suspend fun updateFilesTitle(downloadDataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			titleTxtView.text = downloadDataModel.fileName
			logger.d("updateFilesTitle: Set title to ${downloadDataModel.fileName}")
		}
	}

	private suspend fun updateFilesMetaInfo(dataModel: DownloadDataModel) {
		val downloadId = dataModel.downloadId.toString()
		val cacheDetails = detailsCache.get(downloadId)
		if (cacheDetails != null) {
			logger.d("updateFilesMetaInfo: Cache hit for $downloadId")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				metadataTxtView.text = cacheDetails
				updatePlaybackTimeInfo(dataModel)
			}
			return
		}

		logger.d("updateFilesMetaInfo: Cache miss for $downloadId, generating metadata")
		val category = dataModel.getUpdatedCategoryName(true)
		val fileSize = humanReadableSizeOf(dataModel.fileSize.toDouble())
		val playbackTime = dataModel.mediaFilePlaybackDuration.ifEmpty {
			getAudioPlaybackTimeIfAvailable(dataModel)
		}

		if (dataModel.mediaFilePlaybackDuration.isEmpty() && playbackTime.isNotEmpty()) {
			logger.d("updateFilesMetaInfo: Discovered playback time $playbackTime for $downloadId")
			dataModel.mediaFilePlaybackDuration = playbackTime
			dataModel.updateInStorage()
		}

		val modifyDate = formatLastModifiedDate(dataModel.lastModifiedTimeDate)
		val metaInfoDetail = fromHtmlStringToSpanned(
			layout.context.getString(
				R.string.title_b_b_b_date_b,
				getText(R.string.title_info),
				category.removePrefix("AIO"),
				fileSize, playbackTime, modifyDate
			)
		)
		detailsCache.put(dataModel.downloadId.toString(), metaInfoDetail)
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			metadataTxtView.text = metaInfoDetail
			updatePlaybackTimeInfo(dataModel)
		}
	}

	private suspend fun updatePlaybackTimeInfo(dataModel: DownloadDataModel) {
		val fileName = dataModel.fileName
		val isMedia = isVideoByName(fileName) || isAudioByName(fileName)
		val mediaDuration = dataModel.mediaFilePlaybackDuration.replace("(", "").replace(")", "")
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			if (isMedia && mediaDuration.isNotEmpty()) {
				logger.d("updatePlaybackTimeInfo: Showing duration $mediaDuration for $fileName")
				showView(durationConLayout, true)
				showView(playIndicatorView, true)
				durationTxtView.text = mediaDuration
			} else {
				logger.d("updatePlaybackTimeInfo: Hiding duration for $fileName")
				playIndicatorView.visibility = View.GONE
				durationConLayout.visibility = View.GONE
			}
		}
	}

	private fun isVideoThumbnailNotAllowed(dataModel: DownloadDataModel): Boolean {
		val isVideoHidden = dataModel.globalSettings.downloadHideVideoThumbnail
		val isVideoFile = isVideo(dataModel.getDestinationDocumentFile())
		val result = isVideoFile && isVideoHidden
		if (result) {
			logger.d("isVideoThumbnailNotAllowed: Video thumbnail hidden for privacy")
		}
		return result
	}

	private suspend fun updateFaviconInfo(dataModel: DownloadDataModel) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			faviconImgView.setImageResource(defaultFaviconResId)
		}

		if (isVideoThumbnailNotAllowed(dataModel)) {
			logger.d("updateFaviconInfo: Using default favicon due to privacy settings")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				Glide.with(faviconImgView)
					.load(defaultFaviconResId)
					.placeholder(defaultFaviconResId)
					.into(faviconImgView)
			}
			return
		}

		try {
			aioFavicons.getFavicon(dataModel.siteReferrer)?.let { faviconFilePath ->
				val faviconImgFile = File(faviconFilePath)
				if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
					logger.d("updateFaviconInfo: Favicon file not found at $faviconFilePath")
					return
				}

				logger.d("updateFaviconInfo: Loading favicon from $faviconFilePath")
				val faviconImgURI = faviconImgFile.toUri()
				withContext(Dispatchers.Main) {
					if (!isActive) return@withContext
					showView(faviconImgView, true)
					Glide.with(faviconImgView)
						.load(faviconImgURI)
						.placeholder(defaultFaviconResId)
						.into(faviconImgView)
				}
			} ?: logger.d("updateFaviconInfo: No favicon available for ${dataModel.siteReferrer}")
		} catch (error: Exception) {
			logger.e("updateFaviconInfo: Error loading favicon - ${error.message}", error)
			faviconImgView.setImageResource(defaultFaviconResId)
		}
	}

	private suspend fun updateThumbnailInfo(dataModel: DownloadDataModel) {
		val destinationFile = dataModel.getDestinationFile()
		val defaultThumb = dataModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

		logger.d("updateThumbnailInfo: Starting for ${dataModel.fileName}")

		if (isVideoThumbnailNotAllowed(dataModel)) {
			logger.d("updateThumbnailInfo: Using default thumbnail due to privacy settings")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				Glide.with(thumbImgView)
					.load(defaultThumbDrawable)
					.placeholder(defaultThumbDrawable)
					.into(thumbImgView)
			}
			return
		}

		if (loadApkThumbnail(dataModel, thumbImgView, defaultThumb)) {
			logger.d("updateThumbnailInfo: APK thumbnail loaded successfully")
			return
		}

		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			logger.d("updateThumbnailInfo: Loading cached thumbnail from $cachedThumbPath")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				loadBitmapWithGlide(thumbImgView, dataModel.thumbPath, defaultThumb)
			}
			return
		}

		logger.d("updateThumbnailInfo: Generating new thumbnail for ${dataModel.fileName}")
		val bitmapFromFile = getThumbnailFromFile(
			targetFile = destinationFile,
			thumbnailUrl = dataModel.videoInfo?.videoThumbnailUrl,
			requiredThumbWidth = 420
		)

		if (bitmapFromFile != null) {
			logger.d("updateThumbnailInfo: Thumbnail generated, processing and caching")
			val isPortrait = bitmapFromFile.height > bitmapFromFile.width
			val rotatedBitmap = if (isPortrait) rotateBitmap(bitmapFromFile, 270f) else bitmapFromFile
			val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
			saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
				dataModel.thumbPath = filePath
				dataModel.updateInStorage()
				logger.d("updateThumbnailInfo: Thumbnail saved to $filePath")
				withContext(Dispatchers.Main) {
					if (!isActive) return@withContext
					loadBitmapWithGlide(thumbImgView, dataModel.thumbPath, defaultThumb)
				}
			}
		} else {
			logger.d("updateThumbnailInfo: No thumbnail generated, using default")
		}
	}

	private fun loadBitmapWithGlide(target: ImageView, filePath: String, placeHolder: Int) {
		logger.d("loadBitmapWithGlide: Loading bitmap from $filePath")
		val imgURI = File(filePath).toUri()
		Glide.with(target).load(imgURI).placeholder(placeHolder).into(target)
	}

	private suspend fun updateFileTypeIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			val icon = when {
				isImageByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Image file type")
					R.drawable.ic_button_images
				}
				isAudioByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Audio file type")
					R.drawable.ic_button_audio
				}
				isVideoByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Video file type")
					R.drawable.ic_button_video
				}
				isDocumentByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Document file type")
					R.drawable.ic_button_document
				}
				isArchiveByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Archive file type")
					R.drawable.ic_button_archives
				}
				isProgramByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Program file type")
					R.drawable.ic_button_programs
				}
				else -> {
					logger.d("updateFileTypeIndicator: Generic file type")
					R.drawable.ic_button_file
				}
			}
			Glide.with(fileTypeImgView).load(icon).into(fileTypeImgView)
		}
	}

	private suspend fun updatePrivateFolderIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			val globalSettings = dataModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation
			val isPrivate = downloadLocation == PRIVATE_FOLDER
			val icon = if (isPrivate) {
				logger.d("updatePrivateFolderIndicator: Private folder location")
				R.drawable.ic_button_lock
			} else {
				logger.d("updatePrivateFolderIndicator: Standard folder location")
				R.drawable.ic_button_folder
			}
			Glide.with(privateFolderImgView).load(icon).into(privateFolderImgView)
		}
	}

	private suspend fun updateOpenFileIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			val imgResId = if (!aioSettings.openDownloadedFileOnSingleClick)
				R.drawable.ic_button_open_v2 else R.drawable.ic_button_player
			openFileIndicatorImgView.setImageResource(imgResId)
		}
	}

	private suspend fun loadApkThumbnail(
		dataModel: DownloadDataModel,
		target: ImageView,
		placeHolder: Int
	): Boolean = withContext(Dispatchers.Main) {
		if (!isActive) {
			logger.d("loadApkThumbnail: Coroutine not active")
			return@withContext false
		}

		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			logger.d("loadApkThumbnail: Using cached APK thumbnail")
			loadBitmapWithGlide(target, dataModel.thumbPath, placeHolder)
			return@withContext true
		}

		val apkFile = dataModel.getDestinationFile()
		if (!apkFile.exists() || !apkFile.name.endsWith(".apk", true)) {
			logger.d("loadApkThumbnail: Not an APK file - ${apkFile.name}")
			Glide.with(target).load(placeHolder).into(target)
			return@withContext false
		}

		logger.d("loadApkThumbnail: Extracting APK icon from ${apkFile.name}")
		val packageManager: PackageManager = layout.context.packageManager
		try {
			val packageInfo: PackageInfo? =
				packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)

			packageInfo?.applicationInfo?.let { appInfo ->
				appInfo.sourceDir = apkFile.absolutePath
				appInfo.publicSourceDir = apkFile.absolutePath
				val appIconDrawable: Drawable = appInfo.loadIcon(packageManager)
				Glide.with(target).load(appIconDrawable)
					.placeholder(placeHolder).into(target)

				logger.d("loadApkThumbnail: APK icon loaded, caching for future use")
				withContext(Dispatchers.IO) {
					ViewUtility.drawableToBitmap(appIconDrawable)?.let { bmp ->
						val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
						saveBitmapToFile(bmp, thumbnailName)?.let { filePath ->
							dataModel.thumbPath = filePath
							dataModel.updateInStorage()
							logger.d("loadApkThumbnail: APK thumbnail cached to $filePath")
						}
					}
				}
				true
			} ?: run {
				logger.d("loadApkThumbnail: No package info found for APK")
				false
			}
		} catch (error: Exception) {
			logger.e("loadApkThumbnail: Error extracting APK icon - ${error.message}", error)
			target.apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setPadding(0, 0, 0, 0)
				Glide.with(target).load(placeHolder)
					.placeholder(placeHolder).into(target)
			}
			false
		}
	}
}