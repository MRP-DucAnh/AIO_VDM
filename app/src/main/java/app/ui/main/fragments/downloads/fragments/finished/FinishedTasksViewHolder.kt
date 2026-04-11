package app.ui.main.fragments.downloads.fragments.finished

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.util.LruCache
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.aioSettings
import app.core.engines.downloader.AIODownload
import app.core.engines.downloader.AIODownload.Companion.THUMB_EXTENSION
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import com.aio.R
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import lib.device.DateTimeUtils.formatLastModifiedDate
import lib.files.FileSizeFormatter.humanReadableSizeOf
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideoByName
import lib.networks.DownloaderUtils.getAudioPlaybackTimeIfAvailable
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.fromHtmlStringToSpanned
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.drawableToBitmap
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.normalizeTallSymbols
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.showView
import java.io.File
import java.lang.ref.WeakReference

/**
 * ViewHolder class responsible for managing and binding data to the UI components of a completed download task item.
 *
 * This class handles the asynchronous loading of file metadata, thumbnails, favicons, and status indicators
 * for finished downloads. It utilizes coroutines for background processing (such as image decoding and
 * file I/O) and provides caching mechanisms for media titles and metadata to ensure smooth scrolling
 * and efficient UI updates.
 *
 * @property layout The root [View] representing the individual row item in the finished tasks list.
 * @constructor Initializes the view references and sets up the internal coroutine scope and caches.
 */
class FinishedTasksViewHolder(layout: View) {

	/**
	 * Logger instance used for tracking internal events, errors, and debugging information
	 * specific to the [FinishedTasksViewHolder] class.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the root [View] of this ViewHolder.
	 *
	 * Using a weak reference prevents memory leaks by allowing the [layout]
	 * to be garbage collected even if this ViewHolder instance persists.
	 */
	private val weakReferenceOfLayout = WeakReference(layout)

	/**
	 * Provides a null-safe access to the underlying [View] held by [weakReferenceOfLayout].
	 * Returns the View if it has not been garbage collected, or null otherwise.
	 */
	private val safeLayoutRef: View? get() = weakReferenceOfLayout.get()

	/**
	 * A memory cache for storing formatted media metadata (category, file size, duration, and date).
	 * This prevents re-parsing HTML and re-calculating strings for the metadata [TextView]
	 * during list scrolling, improving UI performance.
	 */
	private val mediaMetadataCache = object : LruCache<String, Spanned>(500) {}

	/**
	 * A memory cache that stores the formatted [Spanned] version of the file names.
	 *
	 * This prevents repeated execution of [lib.ui.ViewUtility.normalizeTallSymbols] and
	 * other text formatting logic when the same items are rebound to the RecyclerView.
	 * The cache uses the download ID as a key and has a maximum capacity of 500 entries.
	 */
	private val mediaTitleCache = object : LruCache<String, Spanned>(500) {}

	/**
	 * A [CoroutineScope] tied to this ViewHolder for managing asynchronous tasks such as
	 * loading thumbnails, metadata, and icons. It uses [SupervisorJob] to ensure that
	 * a failure in one child task doesn't cancel the entire scope, and operates
	 * on [Dispatchers.Main.immediate] for performant UI updates.
	 */
	private val coroutineScope = CoroutineScope(SupervisorJob() + Main.immediate)

	/**
	 * The root [RelativeLayout] for the finished download task list item.
	 * Used for handling click events and managing item interaction states.
	 */
	private var rootConLayout: RelativeLayout? = null

	/**
	 * [ImageView] responsible for displaying the file's thumbnail.
	 * For media files, it displays a generated preview; for APKs, the app icon;
	 * and for other file types, a generic category-based placeholder.
	 */
	private var thumbImgView: ImageView? = null

	/**
	 * [ImageView] used to display the website's favicon associated with the downloaded file.
	 * The favicon is retrieved based on the site referrer stored in the [AIODownload].
	 */
	private var faviconImgView: ImageView? = null

	/**
	 * [TextView] that displays the filename of the finished download task.
	 *
	 * This view supports text normalization for tall symbols and utilizes a cache
	 * ([mediaTitleCache]) to optimize UI updates during list scrolling.
	 */
	private var titleTxtView: TextView? = null

	/**
	 * A container view that holds metadata information for the finished download task,
	 * such as file size, category, and modification date.
	 */
	private var containerMetadataInfo: View? = null

	/**
	 * [TextView] responsible for displaying the metadata information of the downloaded file,
	 * such as file category, size, media duration, and the last modification date.
	 */
	private var metadataTxtView: TextView? = null

	/**
	 * [TextView] used to display the playback duration for media files (audio/video).
	 * This view is toggled visible only when valid duration metadata is available.
	 */
	private var durationTxtView: TextView? = null

	/**
	 * Layout container that holds the [durationTxtView] and [playIndicatorView].
	 * This view is typically shown for media files (audio/video) to display the playback length.
	 */
	private var durationConLayout: View? = null

	/**
	 * An icon view that indicates whether a media file (audio or video) is playable,
	 * typically displayed alongside the media duration.
	 */
	private var playIndicatorView: View? = null

	/**
	 * An [ImageView] that displays an icon representing the file type
	 * (e.g., video, audio, image, document, archive, or program) of the finished task.
	 */
	private var fileTypeImgView: ImageView? = null

	/**
	 * ImageView that displays an icon indicating the action triggered by clicking the item.
	 *
	 * The icon changes based on the user's settings: showing a play icon if single-click
	 * is configured to open/play the file, or a folder/open icon otherwise.
	 */
	private var openFileIndicatorImgView: ImageView? = null

	/**
	 * [ImageView] used to display an icon indicating whether the downloaded file is stored
	 * in a private folder or a standard directory.
	 */
	private var privateFolderImgView: ImageView? = null

	/**
	 * An [ImageView] that displays a visual indicator (typically a dot) to signal that
	 * the downloaded file has not yet been opened or viewed by the user.
	 */
	private var newIndicatorImgView: ImageView? = null

	/**
	 * The resource ID for the placeholder image displayed when no file thumbnail is available.
	 */
	private val noThumbResId = R.drawable.image_no_thumb_available

	init {
		rootConLayout = safeLayoutRef?.findViewById(R.id.button_finish_download_row)
		thumbImgView = safeLayoutRef?.findViewById(R.id.img_file_thumbnail)
		faviconImgView = safeLayoutRef?.findViewById(R.id.img_site_favicon)
		titleTxtView = safeLayoutRef?.findViewById(R.id.txt_file_name)
		containerMetadataInfo = safeLayoutRef?.findViewById(R.id.container_meta_data_info)
		metadataTxtView = safeLayoutRef?.findViewById(R.id.txt_file_info)
		durationTxtView = safeLayoutRef?.findViewById(R.id.txt_media_duration)
		durationConLayout = safeLayoutRef?.findViewById(R.id.container_media_duration)
		playIndicatorView = safeLayoutRef?.findViewById(R.id.img_media_play_indicator)
		fileTypeImgView = safeLayoutRef?.findViewById(R.id.img_file_type_indicator)
		openFileIndicatorImgView = safeLayoutRef?.findViewById(R.id.btn_open_download_file)
		privateFolderImgView = safeLayoutRef?.findViewById(R.id.img_private_folder_indicator)
		newIndicatorImgView = safeLayoutRef?.findViewById(R.id.img_new_indicator)
	}

	/**
	 * Updates the view holder with data from the provided [AIODownload].
	 *
	 * This function handles the asynchronous population of UI elements including titles,
	 * metadata, favicons, file type indicators, and thumbnails. It cancels any previous
	 * pending update jobs before starting new ones to ensure data consistency during
	 * view recycling.
	 *
	 * @param dataModel The model containing the download information to display.
	 * If null, the view is immediately cleared.
	 * @param listener The callback interface for click and long-click events.
	 * If null, the update is aborted.
	 */
	fun updateView(dataModel: AIODownload?, listener: FinishedTasksClickEvents?) {
		if (dataModel == null) {
			clearViewImmediately()
			return
		}

		if (listener == null) return
		setDataImmediately(dataModel)

		coroutineScope.coroutineContext.cancelChildren()
		coroutineScope.launch {
			setupItemClickListeners(listener, dataModel)

			val titleJob = async(Default) { updateFilesTitle(dataModel) }
			val metaJob = async(Default) { updateFilesMetaInfo(dataModel) }
			val faviconJob = async(IO) { updateFaviconInfo(dataModel) }
			val typeJob = async(Default) { updateFileTypeIndicator(dataModel) }
			val privateJob = async(Default) { updatePrivateFolderIndicator(dataModel) }
			val openFileJob = async(Default) { updateOpenFileIndicator(dataModel) }
			val newFileJob = async(Default) { updateNewFileIndicator(dataModel) }
			val thumbJob = async(IO) { updateThumbnailInfo(dataModel) }

			awaitAll(
				titleJob, metaJob, faviconJob, typeJob,
				privateJob, openFileJob, newFileJob, thumbJob
			)
		}
	}

	/**
	 * Sets the initial state of the view components immediately before asynchronous
	 * loading begins. Currently used to reset the thumbnail to a default placeholder.
	 *
	 * @param dataModel The [AIODownload] containing information about the task.
	 */
	private fun setDataImmediately(dataModel: AIODownload) {
		thumbImgView?.setImageResource(noThumbResId)
	}

	/**
	 * Immediately updates the file type icon based on the provided file name.
	 *
	 * This method maps various file extensions to their corresponding drawable resources
	 * (e.g., Image, Audio, Video, Document, Archive, or Program) and sets the resulting
	 * icon to the [fileTypeImgView] without any asynchronous processing or animation.
	 *
	 * @param fileName The name of the file (including extension) used to determine the file type.
	 */
	private fun setFileTypeIndicatorImmediately(fileName: String?) {
		val icon = when {
			isImageByName(fileName) -> R.drawable.ic_button_images
			isAudioByName(fileName) -> R.drawable.ic_button_audio
			isVideoByName(fileName) -> R.drawable.ic_button_video
			isDocumentByName(fileName) -> R.drawable.ic_button_document
			isArchiveByName(fileName) -> R.drawable.ic_button_archives
			isProgramByName(fileName) -> R.drawable.ic_button_programs
			else -> R.drawable.ic_button_file
		}
		fileTypeImgView?.setImageResource(icon)
	}

	/**
	 * Immediately resets all UI components in the [FinishedTasksViewHolder] to their default states.
	 *
	 * This method clears text fields, resets background resources, removes image drawables,
	 * and hides indicators. It is typically used when the data model is null or when the
	 * view is being recycled to prevent "ghosting" or flickering of old data.
	 */
	private fun clearViewImmediately() {
		titleTxtView?.text = ""
		titleTxtView?.setBackgroundResource(R.drawable.rounded_border_color)
		metadataTxtView?.text = ""
		thumbImgView?.setImageDrawable(null)
		fileTypeImgView?.setImageDrawable(null)
		durationConLayout?.visibility = GONE
		playIndicatorView?.visibility = GONE
		newIndicatorImgView?.visibility = GONE
		faviconImgView?.setImageDrawable(null)
	}

	/**
	 * Cleans up resources held by the ViewHolder to prevent memory leaks and stop background tasks.
	 *
	 * This method performs the following cleanup operations:
	 * - Cancels all active coroutines associated with this ViewHolder.
	 * - Clears pending Glide image requests for thumbnails and favicons.
	 * - Nullifies [ImageView] drawables to release bitmap memory.
	 * - Removes click listeners from the root layout.
	 *
	 * @param clearWeakReference If true, also clears the internal caches ([mediaMetadataCache],
	 * [mediaTitleCache]), nullifies the layout [WeakReference], and fully cancels the [coroutineScope].
	 * Use `false` if the ViewHolder might be reused soon (e.g., during rapid scrolling/recycling).
	 */
	fun clearResources(clearWeakReference: Boolean = true) {
		try {
			coroutineScope.coroutineContext.cancelChildren()
			thumbImgView?.let { Glide.with(it).clear(it) }
			faviconImgView?.let { Glide.with(it).clear(it) }

			thumbImgView?.setImageDrawable(null)
			faviconImgView?.setImageDrawable(null)
			newIndicatorImgView?.setImageDrawable(null)

			rootConLayout?.setOnClickListener(null)
			rootConLayout?.setOnLongClickListener(null)
			rootConLayout?.isClickable = false

			if (clearWeakReference) {
				mediaMetadataCache.evictAll()
				mediaTitleCache.evictAll()
				safeLayoutRef?.tag = null
				weakReferenceOfLayout.clear()
				coroutineScope.cancel()
			}
		} catch (error: Exception) {
			logger.e(
				"clearResources: Error during cleanup " +
					"- ${error.message}", error
			)
		}
	}

	/**
	 * Cancels all ongoing background tasks and releases UI resources related to this view holder
	 * without clearing the underlying [weakReferenceOfLayout].
	 *
	 * This is typically used when the view is being recycled or detached but the holder
	 * instance might still be reused.
	 */
	fun cancelAll() {
		clearResources(false)
	}

	/**
	 * Configures click and long-click listeners for the root layout of the view holder.
	 *
	 * This method ensures that the item is interactive and dispatches click events
	 * back to the provided [listener] using the associated [dataModel].
	 *
	 * @param listener The callback interface to handle click and long-click events.
	 * @param dataModel The data associated with the current item being displayed.
	 */
	private suspend fun setupItemClickListeners(
		listener: FinishedTasksClickEvents,
		dataModel: AIODownload
	) {
		withContext(Main) {
			if (!isActive) return@withContext
			rootConLayout?.apply {
				isClickable = true

				setOnClickListener {
					listener.onFinishedDownloadClick(dataModel)
				}

				setOnLongClickListener {
					listener.onFinishedDownloadLongClick(dataModel)
					true
				}
			}
		}
	}

	/**
	 * Updates the title [TextView] with the file name from the provided [AIODownload].
	 *
	 * This function checks the [mediaTitleCache] to avoid redundant text processing. If the title
	 * is not cached, it sets the raw file name and then performs symbol normalization (handling
	 * tall/special characters) asynchronously. The result of the normalization is then cached
	 * for future use.
	 *
	 * @param dataModel The data model containing the file name and download ID.
	 */
	private suspend fun updateFilesTitle(dataModel: AIODownload) {
		withContext(Main) {
			titleTxtView?.let { tv ->
				if (!isActive) return@withContext

				val tv = titleTxtView ?: return@withContext
				val key = dataModel.taskId.toString()
				val cached = mediaTitleCache.get(key)

				if (cached != null && cached.toString() == dataModel.fileName) {
					tv.text = cached
					tv.background = null
					return@withContext
				}

				tv.text = dataModel.fileName
				tv.background = null
				tv.normalizeTallSymbols(scope = coroutineScope) { result ->
					mediaTitleCache.put(key, result)
				}
			}
		}
	}

	/**
	 * Updates the metadata text view with file information such as category, size,
	 * playback duration, and last modified date.
	 *
	 * This function implements a caching mechanism using [mediaMetadataCache] to avoid
	 * redundant string formatting and HTML spanning operations. It also attempts to
	 * retrieve and persist playback duration for media files if it is not already
	 * present in the [dataModel].
	 *
	 * @param dataModel The download data containing the file information to be displayed.
	 */
	private suspend fun updateFilesMetaInfo(dataModel: AIODownload) {
		val downloadId = dataModel.taskId.toString()

		val cacheDetails = mediaMetadataCache.get(downloadId)
		if (cacheDetails != null) {
			withContext(Main) {
				if (!isActive) return@withContext
				metadataTxtView?.text = cacheDetails
				updatePlaybackTimeInfo(dataModel)
			}
			return
		}

		val category = dataModel.getUpdatedCategoryName(true)
		val fileSize = humanReadableSizeOf(dataModel.fileSize.toDouble())

		val playbackTime = dataModel.mediaFilePlaybackDuration.ifEmpty {
			getAudioPlaybackTimeIfAvailable(dataModel)
		}

		val modelInfoEmpty = dataModel.mediaFilePlaybackDuration.isEmpty()
		if (modelInfoEmpty && playbackTime.isNotEmpty()) {
			dataModel.mediaFilePlaybackDuration = playbackTime
			dataModel.updateInDB()
		}

		val modifyDate = formatLastModifiedDate(dataModel.lastModifiedTimeDate)

		val metaInfoDetail = safeLayoutRef?.context?.getString(
			R.string.title_b_b_b_date_b,
			getText(R.string.title_info),
			category.removePrefix("AIO"),
			fileSize, playbackTime, modifyDate
		)?.let { fromHtmlStringToSpanned(it) }

		if (!metaInfoDetail.isNullOrEmpty()) {
			mediaMetadataCache.put(downloadId, metaInfoDetail)

			withContext(Main) {
				if (!isActive) return@withContext
				metadataTxtView?.text = metaInfoDetail
				updatePlaybackTimeInfo(dataModel)
			}
		}
	}

	/**
	 * Updates the UI elements related to media playback duration.
	 *
	 * This function determines if the file is a media type (audio or video) and checks for
	 * valid duration metadata. If valid, it displays the duration text and a play indicator;
	 * otherwise, it hides these views.
	 *
	 * @param dataModel The [AIODownload] containing file information and duration metadata.
	 */
	private suspend fun updatePlaybackTimeInfo(dataModel: AIODownload) {
		val fileName = dataModel.fileName
		val isMedia = isVideoByName(fileName) || isAudioByName(fileName)

		val playbackDuration = dataModel.mediaFilePlaybackDuration
		val mediaDuration = playbackDuration.replace("(", "").replace(")", "")

		withContext(Main) {
			if (!isActive) return@withContext
			if (isMedia && mediaDuration.isNotEmpty()) {
				showView(durationConLayout, true)
				showView(playIndicatorView, true)
				durationTxtView?.text = mediaDuration
			} else {
				playIndicatorView?.visibility = GONE
				durationConLayout?.visibility = GONE
			}
		}
	}

	/**
	 * Checks if displaying a video thumbnail is prohibited based on the user's global settings.
	 *
	 * @param dataModel The [AIODownload] containing the download information and configuration.
	 * @return `true` if the video thumbnail should be hidden, `false` otherwise.
	 */
	private fun isVideoThumbnailNotAllowed(dataModel: AIODownload): Boolean {
		val dataModelConfig = dataModel.config
		val isThumbHidden = dataModelConfig.downloadHideVideoThumbnail
		return isThumbHidden
	}

	/**
	 * Updates the favicon image for a specific download task.
	 *
	 * This function handles the asynchronous loading of a website's favicon based on the
	 * [dataModel]'s site referrer. It performs the following steps:
	 * 1. Resets the [faviconImgView] to a default placeholder.
	 * 2. Checks if video thumbnails/favicons are restricted by user settings.
	 * 3. Attempts to retrieve the favicon file path from the local favicon manager ([aioFavicons]).
	 * 4. Loads the favicon image using Glide with a file signature to handle cache invalidation
	 *    if the file has changed.
	 * 5. Falls back to the default favicon in case of errors or missing files.
	 *
	 * @param dataModel The download data containing the site referrer and configuration settings.
	 */
	private suspend fun updateFaviconInfo(dataModel: AIODownload) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		withContext(Main) {
			if (!isActive) return@withContext
			faviconImgView?.setImageResource(defaultFaviconResId)
		}

		if (isVideoThumbnailNotAllowed(dataModel)) {
			withContext(Main) {
				if (!isActive) return@withContext
				faviconImgView?.let {
					Glide.with(it)
						.load(defaultFaviconResId)
						.placeholder(defaultFaviconResId)
						.into(it)
				}
			}
			return
		}

		try {
			aioFavicons.getFavicon(dataModel.siteReferrer)?.let { faviconFilePath ->
				val faviconImgFile = File(faviconFilePath)
				if (!faviconImgFile.exists() || !faviconImgFile.isFile) return

				val signature = ObjectKey(File(faviconFilePath).lastModified())
				val faviconImgURI = faviconImgFile.toUri()
				withContext(Main) {
					if (!isActive) return@withContext
					showView(faviconImgView, true)

					faviconImgView?.let {
						Glide.with(it)
							.load(faviconImgURI)
							.placeholder(defaultFaviconResId)
							.signature(signature)
							.into(it)
					}
				}
			}
		} catch (error: Exception) {
			logger.e(
				"updateFaviconInfo: Error loading favicon " +
					"- ${error.message}", error
			)
			withContext(Main) {
				if (!isActive) return@withContext
				faviconImgView?.setImageResource(defaultFaviconResId)
			}
		}
	}

	/**
	 * Updates the thumbnail image for a finished download task.
	 *
	 * This function follows a multi-step priority logic to load the thumbnail:
	 * 1. Sets a default category-based placeholder immediately.
	 * 2. Checks if video thumbnails are disabled in global settings.
	 * 3. Attempts to extract an icon if the file is an APK.
	 * 4. Checks for a previously cached thumbnail path in the [AIODownload].
	 * 5. If no cache exists, it generates a new thumbnail from the file (or a remote URL),
	 *    handles orientation (rotating portrait thumbnails), saves the result to internal
	 *    storage, and updates the database.
	 *
	 * @param dataModel The download data containing file paths, IDs, and metadata.
	 */
	private suspend fun updateThumbnailInfo(dataModel: AIODownload) {
		val destinationFile = dataModel.getDestinationFile()
		val defaultThumb = dataModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)
		withContext(Main) { thumbImgView?.setImageDrawable(defaultThumbDrawable) }

		if (isVideoThumbnailNotAllowed(dataModel)) {
			withContext(Main) {
				if (!isActive) return@withContext
				thumbImgView?.let {
					Glide.with(it)
						.load(defaultThumbDrawable)
						.placeholder(defaultThumbDrawable)
						.into(it)
				}
			}
			return
		}

		if (loadApkThumbnail(dataModel, thumbImgView, defaultThumb)) return

		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			withContext(Main) {
				if (!isActive) return@withContext
				loadBitmapWithGlide(
					target = thumbImgView,
					filePath = cachedThumbPath,
					placeHolder = defaultThumb
				)
			}
			return
		}

		val thumbnailResult = withContext(IO) {
			getThumbnailFromFile(
				targetFile = destinationFile,
				fallbackThumbUrl = dataModel.videoInfo?.videoThumbnailUrl,
				requiredThumbWidth = 420
			)
		}

		if (thumbnailResult != null) {
			val isPortrait = thumbnailResult.height > thumbnailResult.width
			val rotatedBitmap = if (isPortrait) {
				rotateBitmap(thumbnailResult, 270f)
			} else {
				thumbnailResult
			}

			val thumbnailName = "${dataModel.taskId}$THUMB_EXTENSION"

			val filePath = withContext(IO) {
				saveBitmapToFile(rotatedBitmap, thumbnailName)
			}

			filePath?.let {
				dataModel.thumbPath = it
				dataModel.updateInDB()

				withContext(Main) {
					if (!isActive) return@withContext
					loadBitmapWithGlide(thumbImgView, dataModel.thumbPath, defaultThumb)
				}
			}
		}
	}

	/**
	 * Loads a bitmap image into the specified [ImageView] using the Glide library.
	 *
	 * This method converts the provided [filePath] into a URI and uses the file's
	 * last modified timestamp as a [ObjectKey] signature to handle efficient
	 * memory and disk caching.
	 *
	 * @param target The [ImageView] where the bitmap should be loaded. If null, no action is taken.
	 * @param filePath The absolute file system path to the image file.
	 * @param placeHolder The resource ID of the drawable to be used as a placeholder while loading.
	 */
	private fun loadBitmapWithGlide(target: ImageView?, filePath: String, placeHolder: Int) {
		target?.let {
			val imgURI = File(filePath).toUri()
			val lastModified = File(filePath).lastModified()
			Glide.with(target)
				.load(imgURI)
				.signature(ObjectKey(lastModified))
				.placeholder(placeHolder)
				.into(target)
		}
	}

	/**
	 * Updates the file type icon displayed in the view holder.
	 *
	 * This function determines the appropriate category icon (Image, Audio, Video, Document,
	 * Archive, or Program) based on the file extension of the [dataModel]. The icon is then
	 * loaded into the [fileTypeImgView] using Glide on the Main thread.
	 *
	 * @param dataModel The download item containing the filename used to determine the file type.
	 */
	private suspend fun updateFileTypeIndicator(dataModel: AIODownload) {
		withContext(Main) {
			if (!isActive) return@withContext

			val icon = when {
				isImageByName(dataModel.fileName) -> R.drawable.ic_button_images
				isAudioByName(dataModel.fileName) -> R.drawable.ic_button_audio
				isVideoByName(dataModel.fileName) -> R.drawable.ic_button_video
				isDocumentByName(dataModel.fileName) -> R.drawable.ic_button_document
				isArchiveByName(dataModel.fileName) -> R.drawable.ic_button_archives
				isProgramByName(dataModel.fileName) -> R.drawable.ic_button_programs
				else -> R.drawable.ic_button_file
			}

			fileTypeImgView?.let {
				Glide.with(it)
					.load(icon)
					.into(it)
			}
		}
	}

	/**
	 * Updates the visual indicator showing whether the downloaded file is stored in a private folder.
	 *
	 * This function checks the download location settings from the [dataModel] and updates
	 * the [privateFolderImgView] using Glide. It displays a specific private folder icon
	 * if the location matches [PRIVATE_FOLDER], otherwise it shows a standard folder icon.
	 *
	 * @param dataModel The data model containing the settings and download location information.
	 */
	private suspend fun updatePrivateFolderIndicator(dataModel: AIODownload) {
		withContext(Main) {
			if (!isActive) return@withContext

			val globalSettings = dataModel.config
			val downloadLocation = globalSettings.defaultDownloadLocationType
			val isPrivate = downloadLocation == PRIVATE_FOLDER

			val icon = if (isPrivate) {
				R.drawable.ic_button_private_folder
			} else {
				R.drawable.ic_button_folder
			}

			privateFolderImgView?.let {
				Glide.with(it)
					.load(icon)
					.into(it)
			}
		}
	}

	/**
	 * Updates the action indicator icon based on the user's interaction settings.
	 *
	 * Displays a "player" icon if a single click is configured to open the file directly,
	 * or an "open" folder/file icon if the default behavior is different.
	 *
	 * @param dataModel The download data containing the settings context for this item.
	 */
	private suspend fun updateOpenFileIndicator(dataModel: AIODownload) {
		withContext(Main) {
			val singleClick = aioSettings.openDownloadedFileOnSingleClick
			val playResId = R.drawable.ic_button_player
			val openResId = R.drawable.ic_button_open_v2
			val imgResId = if (!singleClick) openResId else playResId
			openFileIndicatorImgView?.setImageResource(imgResId)
		}
	}

	/**
	 * Updates the visibility of the "new file" indicator (typically a dot) based on whether
	 * the user has already interacted with or opened the downloaded file.
	 *
	 * It uses [Glide] to load the indicator resource and toggles the visibility of
	 * [newIndicatorImgView] to [VISIBLE] if the file is unopened, or [GONE] otherwise.
	 *
	 * @param dataModel The [AIODownload] containing the file's interaction state.
	 */
	private suspend fun updateNewFileIndicator(dataModel: AIODownload) {
		withContext(Main) {
			newIndicatorImgView?.let {
				Glide.with(it)
					.load(R.drawable.ic_button_dot)
					.placeholder(R.drawable.rounded_transparent)
					.into(it)

				val hasUserOpenedTheFile = dataModel.hasUserOpenedTheFile
				it.visibility = if (hasUserOpenedTheFile) GONE else VISIBLE
			}
		}
	}

	/**
	 * Attempts to load and display the icon of an APK file.
	 *
	 * This function first checks for a cached thumbnail path in the [downloadModel].
	 * If no cache exists, it parses the APK file on disk to extract its application icon,
	 * displays it in the [targetImageView], and asynchronously saves the extracted icon
	 * to a local file for future use.
	 *
	 * @param downloadModel The data model containing file information and thumbnail metadata.
	 * @param targetImageView The [ImageView] where the extracted icon or placeholder should be displayed.
	 * @param placeHolderDrawableResId The resource ID of the drawable to show if the icon cannot be loaded.
	 * @return `true` if a thumbnail (cached or extracted) was successfully loaded; `false` otherwise.
	 */
	private suspend fun loadApkThumbnail(
		downloadModel: AIODownload,
		targetImageView: ImageView?,
		placeHolderDrawableResId: Int
	): Boolean = withContext(Main) {
		if (!isActive) return@withContext false
		if (targetImageView == null) return@withContext false

		val cachedThumbFilePath = downloadModel.thumbPath
		if (cachedThumbFilePath.isNotEmpty()) {
			loadBitmapWithGlide(
				target = targetImageView,
				filePath = downloadModel.thumbPath,
				placeHolder = placeHolderDrawableResId
			)
			return@withContext true
		}

		val apkFile = downloadModel.getDestinationFile()
		if (!apkFile.exists() || !apkFile.name.endsWith(".apk", true)) {
			Glide.with(targetImageView)
				.load(placeHolderDrawableResId)
				.into(targetImageView)
			return@withContext false
		}

		val context = targetImageView.context ?: return@withContext false
		val pm: PackageManager = context.packageManager

		try {
			val flags = PackageManager.GET_ACTIVITIES
			val apkFileAbsolutePath = apkFile.absolutePath
			val pkInfo = pm.getPackageArchiveInfo(apkFileAbsolutePath, flags)

			pkInfo?.applicationInfo?.let { appInfo ->
				appInfo.sourceDir = apkFileAbsolutePath
				appInfo.publicSourceDir = apkFileAbsolutePath
				val appIconDrawable: Drawable = appInfo.loadIcon(pm)

				Glide.with(targetImageView)
					.load(appIconDrawable)
					.placeholder(placeHolderDrawableResId)
					.into(targetImageView)

				withContext(IO) {
					drawableToBitmap(appIconDrawable)?.let { bmp ->
						val thumbnailName = "${downloadModel.taskId}$THUMB_EXTENSION"
						saveBitmapToFile(bmp, thumbnailName)?.let { filePath ->
							downloadModel.thumbPath = filePath
							downloadModel.updateInDB()
						}
					}
				}

				true
			} ?: run { false }

		} catch (error: Exception) {
			logger.e(
				"loadApkThumbnail: Error extracting APK icon " +
					"- ${error.message}", error
			)

			targetImageView.apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setPadding(0, 0, 0, 0)
				Glide.with(targetImageView)
					.load(placeHolderDrawableResId)
					.placeholder(placeHolderDrawableResId)
					.into(targetImageView)
			}

			false
		}
	}
}