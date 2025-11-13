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
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.downloader.DownloadDataModel.Companion.THUMB_EXTENSION
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import com.aio.R
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

	private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
	private val cacheSize = maxMemory / 8 // use 1/8 of available memory
	private val detailsCache = object : LruCache<String, Spanned>(cacheSize) {}
	private var currentCoroutineJob: Job? = null
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private val rootConLayout: RelativeLayout by lazy { layout.findViewById(R.id.button_finish_download_row) }
	private val thumbImgView: ImageView by lazy { layout.findViewById(R.id.img_file_thumbnail) }
	private val faviconImgView: ImageView by lazy { layout.findViewById(R.id.img_site_favicon) }
	private val titleTxtView: TextView by lazy { layout.findViewById(R.id.txt_file_name) }
	private val metadataTxtView: TextView by lazy { layout.findViewById(R.id.txt_file_info) }
	private val durationTxtView: TextView by lazy { layout.findViewById(R.id.txt_media_duration) }
	private val durationConLayout: View by lazy { layout.findViewById(R.id.container_media_duration) }
	private val playIndicatorView: View by lazy { layout.findViewById(R.id.img_media_play_indicator) }
	private val fileTypeImgView: ImageView by lazy { layout.findViewById(R.id.img_file_type_indicator) }
	private val privateFolderImgView: ImageView by lazy { layout.findViewById(R.id.img_private_folder_indicator) }

	fun updateView(
		dataModel: DownloadDataModel,
		eventListener: FinishedTasksClickEvents
	) {
		clearResources()
		currentCoroutineJob = scope.launch {
			refreshDownloadProgress(dataModel)
			setupItemClickEventListeners(eventListener, dataModel)
		}
	}

	fun clearResources() {
		currentCoroutineJob?.cancel()
		Glide.with(thumbImgView).clear(thumbImgView)
		Glide.with(faviconImgView).clear(faviconImgView)
		thumbImgView.setImageDrawable(null)
		faviconImgView.setImageDrawable(null)
		detailsCache.evictAll()
	}

	private suspend fun setupItemClickEventListeners(
		eventListener: FinishedTasksClickEvents,
		dataModel: DownloadDataModel
	) {
		withContext(Dispatchers.Main) {
			rootConLayout.apply {
				isClickable = true
				setOnClickListener { eventListener.onFinishedDownloadClick(dataModel) }
				setOnLongClickListener { eventListener.onFinishedDownloadLongClick(dataModel); true }
			}
		}
	}

	private suspend fun refreshDownloadProgress(dataModel: DownloadDataModel) {
		updateFilesTitle(dataModel)
		updateFilesMetaInfo(dataModel)
		updateFaviconInfo(dataModel)
		updateThumbnailInfo(dataModel)
		updateFileTypeIndicator(dataModel)
		updatePrivateFolderIndicator(dataModel)
	}

	private suspend fun updateFilesTitle(downloadDataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			titleTxtView.apply { text = downloadDataModel.fileName }
		}
	}

	private suspend fun updateFilesMetaInfo(dataModel: DownloadDataModel) {
		val cacheDetails = detailsCache.get(dataModel.downloadId.toString())
		if (cacheDetails != null) {
			withContext(Dispatchers.Main) {
				metadataTxtView.text = cacheDetails
				updatePlaybackTimeInfo(dataModel)
			}
			return
		}

		val category = dataModel.getUpdatedCategoryName(shouldRemoveAIOPrefix = true)
		val fileSize = humanReadableSizeOf(dataModel.fileSize.toDouble())
		val playbackTime = dataModel.mediaFilePlaybackDuration.ifEmpty {
			getAudioPlaybackTimeIfAvailable(dataModel)
		}

		if (dataModel.mediaFilePlaybackDuration.isEmpty() && playbackTime.isNotEmpty()) {
			dataModel.mediaFilePlaybackDuration = playbackTime
			dataModel.updateInStorage()
		}

		val modifyDate = formatLastModifiedDate(dataModel.lastModifiedTimeDate)
		val metaInfoDetail = fromHtmlStringToSpanned(
			layout.context.getString(
				R.string.title_b_b_b_date_b,
				getText(R.string.title_info), category.removePrefix(prefix = "AIO"),
				fileSize, playbackTime, modifyDate
			)
		)
		detailsCache.put(dataModel.downloadId.toString(), metaInfoDetail)
		withContext(Dispatchers.Main) {
			metadataTxtView.text = metaInfoDetail
			updatePlaybackTimeInfo(dataModel)
		}
	}

	private suspend fun updatePlaybackTimeInfo(dataModel: DownloadDataModel) {
		val fileName = dataModel.fileName
		val isMedia = isVideoByName(fileName) || isAudioByName(fileName)
		val mediaDuration = dataModel.mediaFilePlaybackDuration.replace("(", "").replace(")", "")
		withContext(Dispatchers.Main) {
			if (isMedia && mediaDuration.isNotEmpty()) {
				showView(durationConLayout, true)
				showView(playIndicatorView, true)
				durationTxtView.text = mediaDuration
			} else {
				playIndicatorView.visibility = View.GONE
				durationConLayout.visibility = View.GONE
			}
		}
	}

	private fun isVideoThumbnailNotAllowed(dataModel: DownloadDataModel): Boolean {
		val isVideoHidden = dataModel.globalSettings.downloadHideVideoThumbnail
		return isVideo(dataModel.getDestinationDocumentFile()) && isVideoHidden
	}

	private suspend fun updateFaviconInfo(dataModel: DownloadDataModel) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		withContext(Dispatchers.Main) { faviconImgView.setImageResource(defaultFaviconResId) }

		if (isVideoThumbnailNotAllowed(dataModel = dataModel)) {
			withContext(Dispatchers.Main) {
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
					return
				}

				val faviconImgURI = faviconImgFile.toUri()
				withContext(Dispatchers.Main) {
					showView(targetView = faviconImgView, shouldAnimate = true)
					Glide.with(faviconImgView)
						.load(faviconImgURI)
						.placeholder(defaultFaviconResId)
						.into(faviconImgView)
				}
			}
		} catch (error: Exception) {
			logger.e("Error loading favicon: ${error.message}", error)
			faviconImgView.setImageResource(defaultFaviconResId)
		}
	}

	private suspend fun updateThumbnailInfo(dataModel: DownloadDataModel) {
		val destinationFile = dataModel.getDestinationFile()
		val defaultThumb = dataModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

		if (isVideoThumbnailNotAllowed(dataModel)) {
			withContext(Dispatchers.Main) {
				Glide.with(thumbImgView)
					.load(defaultThumbDrawable)
					.placeholder(defaultThumbDrawable)
					.into(thumbImgView)
			}
			return
		}

		if (loadApkThumbnail(dataModel, thumbImgView, defaultThumb)) return

		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			withContext(Dispatchers.Main) {
				loadBitmapWithGlide(
					target = thumbImgView,
					filePath = dataModel.thumbPath,
					placeHolder = defaultThumb
				)
			}
			return
		}

		val bitmapFromFile = getThumbnailFromFile(
			targetFile = destinationFile,
			thumbnailUrl = dataModel.videoInfo?.videoThumbnailUrl,
			requiredThumbWidth = 420
		)

		if (bitmapFromFile != null) {
			val isPortrait = bitmapFromFile.height > bitmapFromFile.width
			val rotatedBitmap = if (isPortrait) rotateBitmap(bitmapFromFile, 270f) else bitmapFromFile
			val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
			saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
				dataModel.thumbPath = filePath
				dataModel.updateInStorage()
				withContext(Dispatchers.Main) {
					loadBitmapWithGlide(
						target = thumbImgView,
						filePath = dataModel.thumbPath,
						placeHolder = defaultThumb
					)
				}
			}
		}
	}

	private fun loadBitmapWithGlide(target: ImageView, filePath: String, placeHolder: Int) {
		val imgURI = File(filePath).toUri()
		Glide.with(target).load(imgURI).placeholder(placeHolder).into(target)
	}

	private suspend fun updateFileTypeIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			Glide.with(fileTypeImgView).load(
				when {
					isImageByName(dataModel.fileName) -> R.drawable.ic_button_images
					isAudioByName(dataModel.fileName) -> R.drawable.ic_button_audio
					isVideoByName(dataModel.fileName) -> R.drawable.ic_button_video
					isDocumentByName(dataModel.fileName) -> R.drawable.ic_button_document
					isArchiveByName(dataModel.fileName) -> R.drawable.ic_button_archives
					isProgramByName(dataModel.fileName) -> R.drawable.ic_button_programs
					else -> R.drawable.ic_button_file
				}
			).into(fileTypeImgView)
		}
	}

	private suspend fun updatePrivateFolderIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			val globalSettings = dataModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation
			Glide.with(fileTypeImgView).load(
				when (downloadLocation) {
					PRIVATE_FOLDER -> R.drawable.ic_button_lock
					else -> R.drawable.ic_button_folder
				}
			).into(privateFolderImgView)
		}
	}

	private suspend fun loadApkThumbnail(dataModel: DownloadDataModel, target: ImageView, placeHolder: Int): Boolean {
		withContext(Dispatchers.Main) {
			// First check if we already have a cached thumbnail
			val cachedThumbPath = dataModel.thumbPath
			if (cachedThumbPath.isNotEmpty()) {
				loadBitmapWithGlide(
					target = target,
					filePath = dataModel.thumbPath,
					placeHolder = placeHolder
				)
				return@withContext true
			}

			val apkFile = dataModel.getDestinationFile()
			if (!apkFile.exists() || !apkFile.name.lowercase().endsWith(suffix = ".apk")) {
				Glide.with(target).load(placeHolder).into(target)
				return@withContext false
			}

			val packageManager: PackageManager = layout.context.packageManager
			return@withContext try {
				val getActivities = PackageManager.GET_ACTIVITIES
				val apkFileAbsolutePath = apkFile.absolutePath
				val packageInfo: PackageInfo? =
					packageManager.getPackageArchiveInfo(apkFileAbsolutePath, getActivities)

				packageInfo?.applicationInfo?.let { appInfo ->
					appInfo.sourceDir = apkFileAbsolutePath
					appInfo.publicSourceDir = apkFileAbsolutePath
					val appIconDrawable: Drawable = appInfo.loadIcon(packageManager)
					Glide.with(target).load(appIconDrawable)
						.placeholder(placeHolder).into(target)

					// Save the APK icon as a thumbnail for future use
					withContext(Dispatchers.IO) {
						ViewUtility.drawableToBitmap(appIconDrawable)?.let {
							val appIconBitmap = it
							val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
							saveBitmapToFile(appIconBitmap, thumbnailName)?.let { filePath ->
								dataModel.thumbPath = filePath
								dataModel.updateInStorage()
							}
						}
					}
					true
				} ?: run { false }
			} catch (error: Exception) {
				logger.e("Error loading APK thumbnail: ${error.message}", error)
				target.apply {
					scaleType = ImageView.ScaleType.FIT_CENTER
					setPadding(0, 0, 0, 0)
					Glide.with(target).load(placeHolder)
						.placeholder(placeHolder).into(target)
				}
				false
			}
		}
		return false
	}
}