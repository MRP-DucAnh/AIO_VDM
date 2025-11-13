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
import lib.process.AsyncJobUtils.executeOnMainThread
import lib.process.LogHelperUtils
import lib.process.ThreadsUtility.executeInBackground
import lib.process.ThreadsUtility.executeOnMain
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
	private val detailsCache = object : LruCache<String, Spanned>(1024) {}

	private val rootContainerLayout: RelativeLayout by lazy { layout.findViewById(R.id.button_finish_download_row) }
	private val thumbnailImageView: ImageView by lazy { layout.findViewById(R.id.img_file_thumbnail) }
	private val faviconImageView: ImageView by lazy { layout.findViewById(R.id.img_site_favicon) }
	private val titleTextView: TextView by lazy { layout.findViewById(R.id.txt_file_name) }
	private val fileInfoTextView: TextView by lazy { layout.findViewById(R.id.txt_file_info) }
	private val durationTextView: TextView by lazy { layout.findViewById(R.id.txt_media_duration) }
	private val durationContainerLayout: View by lazy { layout.findViewById(R.id.container_media_duration) }
	private val mediaIndicatorView: View by lazy { layout.findViewById(R.id.img_media_play_indicator) }
	private val fileTypeIndicatorImageView: ImageView by lazy { layout.findViewById(R.id.img_file_type_indicator) }
	private val privateFolderImageView: ImageView by lazy { layout.findViewById(R.id.img_private_folder_indicator) }

	fun updateView(downloadModel: DownloadDataModel,
		onClickEventListener: FinishedTasksClickEvents
	) {
		refreshDownloadProgress(downloadModel)
		setupItemClickEventListeners(onClickEventListener, downloadModel)
	}

	fun clearResources() {
		Glide.with(thumbnailImageView).clear(thumbnailImageView)
		Glide.with(faviconImageView).clear(faviconImageView)
		thumbnailImageView.setImageDrawable(null)
		faviconImageView.setImageDrawable(null)
	}

	private fun refreshDownloadProgress(downloadDataModel: DownloadDataModel) {
		updateFilesTitle(downloadDataModel)
		updateFilesMetaInfo(downloadDataModel)
		updateFaviconInfo(downloadDataModel)
		updateThumbnailInfo(downloadDataModel)
		updateFileTypeIndicator(downloadDataModel)
		updatePrivateFolderIndicator(downloadDataModel)
	}

	private fun setupItemClickEventListeners(
		eventsListener: FinishedTasksClickEvents,
		downloadModel: DownloadDataModel
	) {
		rootContainerLayout.apply {
			isClickable = true
			setOnClickListener { null }
			setOnClickListener { eventsListener.onFinishedDownloadClick(downloadModel) }
			setOnLongClickListener(View.OnLongClickListener {
				eventsListener.onFinishedDownloadLongClick(downloadModel)
				return@OnLongClickListener true
			})
		}
	}

	private fun updateFilesTitle(downloadDataModel: DownloadDataModel) {
		titleTextView.apply { text = downloadDataModel.fileName }
	}

	private fun updateFilesMetaInfo(downloadModel: DownloadDataModel) {
		executeInBackground(timeOutInMilli = 2000L, codeBlock = {
			val cacheDetails = detailsCache.get(downloadModel.downloadId.toString())
			cacheDetails?.let {
				executeOnMain {
					fileInfoTextView.text = cacheDetails
					updatePlaybackTimeInfo(downloadModel)
				}
				return@executeInBackground
			}

			val category = downloadModel.getUpdatedCategoryName(shouldRemoveAIOPrefix = true)
			val fileSize = humanReadableSizeOf(downloadModel.fileSize.toDouble())
			val playbackTime = downloadModel.mediaFilePlaybackDuration.ifEmpty {
				getAudioPlaybackTimeIfAvailable(downloadModel)
			}

			if (downloadModel.mediaFilePlaybackDuration.isEmpty() && playbackTime.isNotEmpty()) {
				downloadModel.mediaFilePlaybackDuration = playbackTime
				downloadModel.updateInStorage()
			}

			val modifyDate = formatLastModifiedDate(downloadModel.lastModifiedTimeDate)
			executeOnMain {
				fileInfoTextView.apply {
					val detail = fromHtmlStringToSpanned(
						context.getString(
							R.string.title_b_b_b_date_b,
							getText(R.string.title_info), category.removePrefix("AIO"),
							fileSize, playbackTime, modifyDate
						)
					)
					detailsCache.put(downloadModel.downloadId.toString(), detail)
					fileInfoTextView.text = detail
					updatePlaybackTimeInfo(downloadModel)
				}
			}
		})
	}

	private fun updatePlaybackTimeInfo(downloadDataModel: DownloadDataModel) {
		executeInBackground(timeOutInMilli = 2000L, codeBlock = {
			val fileName = downloadDataModel.fileName
			if (isVideoByName(fileName) || isAudioByName(fileName)) {
				val cleanedData = downloadDataModel.mediaFilePlaybackDuration
					.replace("(", "")
					.replace(")", "")
				if (cleanedData.isNotEmpty()) {
					executeOnMain {
						showView(targetView = durationContainerLayout, shouldAnimate = true)
						showView(targetView = mediaIndicatorView, shouldAnimate = true)
						durationTextView.text = cleanedData
					}
				} else {
					executeOnMain {
						showView(targetView = durationContainerLayout, shouldAnimate = false)
						showView(targetView = mediaIndicatorView, shouldAnimate = false)
					}
				}
			} else {
				executeOnMain {
					mediaIndicatorView.visibility = View.GONE
					durationContainerLayout.visibility = View.GONE
				}
			}
		})
	}

	/**
	 * Checks if video thumbnails are allowed for this download based on settings.
	 *
	 * @param downloadDataModel The download data model
	 * @return true if thumbnails are not allowed, false otherwise
	 */
	private fun isVideoThumbnailNotAllowed(downloadDataModel: DownloadDataModel): Boolean {
		val isVideoHidden = downloadDataModel.globalSettings.downloadHideVideoThumbnail
		val result = isVideo(downloadDataModel.getDestinationDocumentFile()) && isVideoHidden
		logger.d("Video thumbnail allowed: ${!result}")
		return result
	}

	private fun updateFaviconInfo(downloadModel: DownloadDataModel) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		executeInBackground(timeOutInMilli = 1000L, codeBlock = {
			executeOnMain { faviconImageView.setImageResource(defaultFaviconResId) }

			if (isVideoThumbnailNotAllowed(downloadDataModel = downloadModel)) {
				executeOnMain(codeBlock = {
					Glide.with(faviconImageView)
						.load(defaultFaviconResId)
						.into(faviconImageView)
				})
				return@executeInBackground
			}

			val referralSite = downloadModel.siteReferrer
			aioFavicons.getFavicon(referralSite)?.let { faviconFilePath ->
				val faviconImgFile = File(faviconFilePath)
				if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
					return@executeInBackground
				}

				val faviconImgURI = faviconImgFile.toUri()
				executeOnMain(codeBlock = {
					showView(targetView = faviconImageView, shouldAnimate = true)
					Glide.with(faviconImageView).load(faviconImgURI).into(faviconImageView)
				})
			}
		}, errorHandler = { error ->
			logger.e("Error loading favicon: ${error.message}", error)
			faviconImageView.setImageResource(defaultFaviconResId)
		})
	}

	private fun updateThumbnailInfo(downloadDataModel: DownloadDataModel) {
		val destinationFile = downloadDataModel.getDestinationFile()
		val defaultThumb = downloadDataModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

		if (isVideoThumbnailNotAllowed(downloadDataModel)) {
			Glide.with(thumbnailImageView)
				.load(defaultThumbDrawable)
				.placeholder(defaultThumbDrawable)
				.into(thumbnailImageView)
			return
		}

		if (loadApkThumbnail(
				downloadDataModel = downloadDataModel,
				imageViewHolder = thumbnailImageView,
				defaultThumbDrawable = defaultThumb
			)) return

		executeInBackground(timeOutInMilli = 2000L, codeBlock = {
			val cachedThumbPath = downloadDataModel.thumbPath
			if (cachedThumbPath.isNotEmpty()) {
				executeOnMain(codeBlock = {
					loadBitmapWithGlide(
						targetImageView = thumbnailImageView,
						thumbFilePath = downloadDataModel.thumbPath,
						defaultThumb = defaultThumb
					)
				})
				return@executeInBackground
			}

			val bitmap = getThumbnailFromFile(
				targetFile = destinationFile,
				thumbnailUrl = downloadDataModel.videoInfo?.videoThumbnailUrl,
				requiredThumbWidth = 420
			)

			if (bitmap != null) {
				val isPortrait = bitmap.height > bitmap.width
				val rotatedBitmap = if (isPortrait) {
					logger.d("Rotating portrait thumbnail")
					rotateBitmap(bitmap, 270f)
				} else {
					bitmap
				}

				val thumbnailName = "${downloadDataModel.downloadId}$THUMB_EXTENSION"
				saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
					logger.d("Saved new thumbnail to: $filePath")
					downloadDataModel.thumbPath = filePath
					downloadDataModel.updateInStorage()
					executeOnMain(codeBlock = {
						loadBitmapWithGlide(
							targetImageView = thumbnailImageView,
							thumbFilePath = downloadDataModel.thumbPath,
							defaultThumb = defaultThumb
						)
					})
				}
			}
		})
	}

	private fun updateFileTypeIndicator(downloadDataModel: DownloadDataModel) {
		Glide.with(fileTypeIndicatorImageView).load(
			// Determine the correct icon by checking file type via file name
			when {
				isImageByName(downloadDataModel.fileName) -> R.drawable.ic_button_images   // Image files
				isAudioByName(downloadDataModel.fileName) -> R.drawable.ic_button_audio    // Audio files
				isVideoByName(downloadDataModel.fileName) -> R.drawable.ic_button_video    // Video files
				isDocumentByName(downloadDataModel.fileName) -> R.drawable.ic_button_document // Documents
				isArchiveByName(downloadDataModel.fileName) -> R.drawable.ic_button_archives  // Archives
				isProgramByName(downloadDataModel.fileName) -> R.drawable.ic_button_programs  // Executables/programs
				else -> R.drawable.ic_button_file // Default for unknown file types
			}
		).into(fileTypeIndicatorImageView)
	}

	private fun updatePrivateFolderIndicator(downloadModel: DownloadDataModel) {
		val downloadLocation = downloadModel.globalSettings.defaultDownloadLocation
		Glide.with(fileTypeIndicatorImageView).load(
			when (downloadLocation) {
				PRIVATE_FOLDER -> R.drawable.ic_button_lock
				else -> R.drawable.ic_button_folder
			}
		).into(privateFolderImageView)
	}

	private fun loadBitmapWithGlide(
		targetImageView: ImageView,
		thumbFilePath: String,
		defaultThumb: Int
	) {
		try {
			logger.d("Loading thumbnail from: $thumbFilePath")
			val imgURI = File(thumbFilePath).toUri()
			Glide.with(targetImageView)
				.load(imgURI)
				.placeholder(defaultThumb)
				.into(targetImageView)
		} catch (error: Exception) {
			logger.e("Error loading thumbnail: ${error.message}", error)
			Glide.with(targetImageView)
				.load(defaultThumb)
				.into(targetImageView)
		}
	}

	private fun loadApkThumbnail(
		downloadDataModel: DownloadDataModel,
		imageViewHolder: ImageView,
		defaultThumbDrawable: Int
	): Boolean {
		// First check if we already have a cached thumbnail
		val cachedThumbPath = downloadDataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			logger.d("Using cached thumbnail")
			executeOnMainThread {
				loadBitmapWithGlide(
					targetImageView = imageViewHolder,
					thumbFilePath = downloadDataModel.thumbPath,
					defaultThumb = defaultThumbDrawable
				)
			}; return true
		}

		logger.d("Checking for APK thumbnail")
		val apkFile = downloadDataModel.getDestinationFile()
		if (!apkFile.exists() || !apkFile.name.lowercase().endsWith(".apk")) {
			logger.d("Not an APK file or doesn't exist")
			Glide.with(imageViewHolder).load(defaultThumbDrawable)
				.placeholder(defaultThumbDrawable).into(imageViewHolder)
			return false
		}

		val packageManager: PackageManager = layout.context.packageManager
		return try {
			logger.d("Loading APK package info")
			val getActivities = PackageManager.GET_ACTIVITIES
			val apkFileAbsolutePath = apkFile.absolutePath
			val packageInfo: PackageInfo? =
				packageManager.getPackageArchiveInfo(apkFileAbsolutePath, getActivities)

			packageInfo?.applicationInfo?.let { appInfo ->
				logger.d("Found APK package info")
				appInfo.sourceDir = apkFileAbsolutePath
				appInfo.publicSourceDir = apkFileAbsolutePath
				val appIconDrawable: Drawable = appInfo.loadIcon(packageManager)
				Glide.with(imageViewHolder).load(appIconDrawable)
					.placeholder(defaultThumbDrawable).into(imageViewHolder)

				// Save the APK icon as a thumbnail for future use
				executeInBackground(codeBlock = {
					ViewUtility.drawableToBitmap(appIconDrawable)?.let {
						val appIconBitmap = it
						val thumbnailName = "${downloadDataModel.downloadId}$THUMB_EXTENSION"
						saveBitmapToFile(appIconBitmap, thumbnailName)?.let { filePath ->
							logger.d("Saved new thumbnail to: $filePath")
							downloadDataModel.thumbPath = filePath
							downloadDataModel.updateInStorage()
						}
					}
				})
				true
			} ?: run {
				logger.d("No package info found")
				false
			}
		} catch (error: Exception) {
			logger.e("Error loading APK thumbnail: ${error.message}", error)
			imageViewHolder.apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setPadding(0, 0, 0, 0)
				Glide.with(imageViewHolder).load(defaultThumbDrawable)
					.placeholder(defaultThumbDrawable).into(imageViewHolder)
			}
			false
		}
	}
}