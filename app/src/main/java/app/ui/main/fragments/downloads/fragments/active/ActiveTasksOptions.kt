package app.ui.main.fragments.downloads.fragments.active

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.downloadSystem
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.downloader.DownloadDataModel.Companion.THUMB_EXTENSION
import app.core.engines.downloader.DownloadStatus.DOWNLOADING
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.video_parser.dialogs.VideoLinkPasteEditor
import app.core.engines.video_parser.parsers.VideoFormat
import app.core.engines.video_parser.parsers.VideoInfo
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.dialogs.DownloadFileRenamer
import app.ui.main.fragments.downloads.dialogs.DownloadInfoTracker
import app.ui.others.media_player.MediaPlayerActivity
import app.ui.others.media_player.MediaPlayerActivity.Companion.INTENT_EXTRA_STREAM_TITLE
import app.ui.others.media_player.MediaPlayerActivity.Companion.INTENT_EXTRA_STREAM_URL
import com.aio.R
import com.aio.R.layout
import com.aio.R.string
import com.yausername.youtubedl_android.YoutubeDL.getInstance
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import lib.device.ShareUtility.shareUrl
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isVideoByName
import lib.networks.URLUtility.isValidURL
import lib.process.AsyncJobUtils.executeInBackground
import lib.process.AsyncJobUtils.executeOnMainThread
import lib.process.LogHelperUtils
import lib.process.ThreadsUtility
import lib.texts.ClipboardUtils.copyTextToClipboard
import lib.texts.CommonTextUtils.getText
import lib.ui.ActivityAnimator.animActivitySwipeLeft
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.MsgDialogUtils.showMessageDialog
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.setRightSideDrawable
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import lib.ui.builders.WaitingDialog
import java.io.File
import java.lang.ref.WeakReference

class ActiveTasksOptions(private val motherActivity: MotherActivity?) {

	private val logger = LogHelperUtils.from(javaClass)
	private val activityWeakRef = motherActivity?.let { WeakReference(it) }

	private val dialogBuilder: DialogBuilder = DialogBuilder(getSafeActivity())
	private var downloadDataModel: DownloadDataModel? = null
	private lateinit var downloadFileRenamer: DownloadFileRenamer
	private lateinit var downloadInfoTracker: DownloadInfoTracker

	private fun getSafeActivity(): MotherActivity? = activityWeakRef?.get()

	init {
		setupDialogClickListeners()
	}

	fun show(downloadModel: DownloadDataModel) {
		if (!dialogBuilder.isShowing) {
			downloadDataModel = downloadModel
			dialogBuilder.show()
			updateDialogFileInfo(downloadModel)
		}
	}

	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
		}
	}

	private fun updateDialogFileInfo(downloadModel: DownloadDataModel) {
		dialogBuilder.view.apply {
			findViewById<TextView>(R.id.txt_file_title).apply {
				isSelected = true
				text = downloadModel.fileName
			}

			findViewById<TextView>(R.id.txt_file_url).apply {
				text = downloadModel.fileURL
			}

			findViewById<ImageView>(R.id.img_file_thumbnail).apply {
				updateThumbnail(this, downloadModel)
			}

			findViewById<ImageView>(R.id.img_file_type_indicator).apply {
				updateFileTypeIndicator(this, downloadModel)
			}

			findViewById<ImageView>(R.id.img_private_folder_indicator).apply {
				updatePrivateFolderIndicator(this, downloadModel)
			}

			findViewById<ImageView>(R.id.img_site_favicon).apply {
				updateFaviconInfo(this, downloadModel)
			}

			findViewById<ImageView>(R.id.img_media_play_indicator).apply {
				updateMediaPlayIndicator(this, downloadModel)
			}

			if (isMediaFile(downloadModel)) {
				findViewById<View>(R.id.container_media_duration).apply {
					val txtMediaPlaybackDuration = findViewById<TextView>(R.id.txt_media_duration)
					val mediaFilePlaybackDuration = downloadModel.mediaFilePlaybackDuration
					val playbackTimeString = mediaFilePlaybackDuration.replace("(", "").replace(")", "")
					if (playbackTimeString.isNotEmpty()) {
						showView(this, true)
						showView(txtMediaPlaybackDuration, true)
						txtMediaPlaybackDuration.text = playbackTimeString
					}
				}
			} else {
				findViewById<View>(R.id.container_media_duration).visibility = View.GONE
			}

			refreshToggleSwitchUI()
		}
	}

	private fun isMediaFile(downloadModel: DownloadDataModel): Boolean =
		isAudioByName(downloadModel.fileName) || isVideoByName(downloadModel.fileName)

	private fun updateMediaPlayIndicator(
		mediaPlayIndicator: ImageView,
		downloadDataModel: DownloadDataModel
	) {
		mediaPlayIndicator.visibility = if (isMediaFile(downloadDataModel)) {
			View.VISIBLE
		} else {
			View.GONE
		}
	}

	private fun updateFileTypeIndicator(
		fileTypeIndicator: ImageView,
		downloadDataModel: DownloadDataModel
	) {
		fileTypeIndicator.setImageResource(
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

	private fun updatePrivateFolderIndicator(privateFolderImageView: ImageView, downloadModel: DownloadDataModel) {
		val downloadLocation = downloadModel.globalSettings.defaultDownloadLocation

		privateFolderImageView.setImageResource(
			when (downloadLocation) {
				PRIVATE_FOLDER -> R.drawable.ic_button_lock
				else -> R.drawable.ic_button_folder
			}
		)
	}

	private fun updateFaviconInfo(favicon: ImageView, downloadDataModel: DownloadDataModel) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		val defaultFaviconDrawable = getDrawable(INSTANCE.resources, defaultFaviconResId, null)

		if (isVideoThumbnailNotAllowed(downloadDataModel)) {
			executeOnMainThread { favicon.setImageDrawable(defaultFaviconDrawable) }
			return
		}

		ThreadsUtility.executeInBackground(codeBlock = {
			val referralSite = downloadDataModel.siteReferrer
			aioFavicons.getFavicon(referralSite)?.let { faviconFilePath ->
				val faviconImgFile = File(faviconFilePath)
				if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
					return@executeInBackground
				}
				val faviconImgURI = faviconImgFile.toUri()
				ThreadsUtility.executeOnMain(codeBlock = {
					try {
						showView(favicon, true)
						favicon.setImageURI(faviconImgURI)
					} catch (error: Exception) {
						error.printStackTrace()
						showView(favicon, true)
						favicon.setImageResource(defaultFaviconResId)
					}
				})
			}
		}, errorHandler = {
			favicon.setImageDrawable(defaultFaviconDrawable)
		})
	}

	private fun isVideoThumbnailNotAllowed(downloadDataModel: DownloadDataModel): Boolean {
		val isVideoHidden = downloadDataModel.globalSettings.downloadHideVideoThumbnail
		return isVideo(downloadDataModel.getDestinationDocumentFile()) && isVideoHidden
	}

	private fun updateThumbnail(thumbImageView: ImageView, downloadModel: DownloadDataModel) {
		val destinationFile = downloadModel.getDestinationFile()
		val defaultThumb = downloadModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

		if (loadApkThumbnail(
				downloadModel = downloadModel,
				imageViewHolder = thumbImageView,
				defaultThumbDrawable = defaultThumbDrawable
			)
		) {
			return
		}

		ThreadsUtility.executeInBackground(codeBlock = {
			val cachedThumbPath = downloadModel.thumbPath
			if (cachedThumbPath.isNotEmpty()) {
				executeOnMainThread {
					loadBitmapWithGlide(thumbImageView, downloadModel.thumbPath, defaultThumb)
				}
				return@executeInBackground
			}

			val thumbnailUrl = downloadModel.videoInfo?.videoThumbnailUrl
			val bitmap = getThumbnailFromFile(destinationFile, thumbnailUrl, 420)

			if (bitmap != null) {
				val isPortrait = bitmap.height > bitmap.width
				val rotatedBitmap = if (isPortrait) {
					rotateBitmap(bitmap = bitmap, angle = 270f)
				} else bitmap

				val thumbnailName = "${downloadModel.downloadId}$THUMB_EXTENSION"
				saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
					downloadModel.thumbPath = filePath
					downloadModel.updateInStorage()

					executeOnMainThread {
						loadBitmapWithGlide(
							thumbImageView = thumbImageView,
							thumbFilePath = downloadModel.thumbPath,
							defaultThumb = defaultThumb
						)
					}
				}
			}
		})
	}

	private fun loadBitmapWithGlide(
		thumbImageView: ImageView,
		thumbFilePath: String,
		defaultThumb: Int
	) {
		try {
			val imgURI = File(thumbFilePath).toUri()
			thumbImageView.setImageURI(imgURI)
		} catch (error: Exception) {
			thumbImageView.setImageResource(defaultThumb)
		}
	}

	private fun loadApkThumbnail(
		downloadModel: DownloadDataModel,
		imageViewHolder: ImageView,
		defaultThumbDrawable: Drawable?
	): Boolean {
		getSafeActivity()?.let { safeMotherActivityRef ->
			val apkFile = downloadModel.getDestinationFile()

			if (!apkFile.exists() || !apkFile.name.lowercase().endsWith(".apk")) {
				imageViewHolder.setImageDrawable(defaultThumbDrawable)
				return false
			}

			val packageManager: PackageManager = safeMotherActivityRef.packageManager
			return try {
				val packageInfo: PackageInfo? = packageManager.getPackageArchiveInfo(
					apkFile.absolutePath,
					PackageManager.GET_ACTIVITIES
				)

				packageInfo?.applicationInfo?.let { appInfo ->
					appInfo.sourceDir = apkFile.absolutePath
					appInfo.publicSourceDir = apkFile.absolutePath

					val icon: Drawable = appInfo.loadIcon(packageManager)
					imageViewHolder.setImageDrawable(icon)
					true
				} ?: run {
					false
				}
			} catch (error: Exception) {
				imageViewHolder.apply {
					scaleType = ImageView.ScaleType.FIT_CENTER
					setPadding(0, 0, 0, 0)
					setImageDrawable(defaultThumbDrawable)
				}
				false
			}
		}
		return false
	}

	@OptIn(UnstableApi::class)
	private fun playTheMedia() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			close()

			if (downloadDataModel?.videoInfo != null && downloadDataModel?.videoFormat != null) {
				val waitingDialog = WaitingDialog(
					baseActivityInf = safeMotherActivityRef,
					loadingMessage = getText(string.title_preparing_video_please_wait)
				)
				waitingDialog.show()

				executeInBackground {
					val videoUrl = downloadDataModel!!.videoInfo!!.videoUrl
					val request = YoutubeDLRequest(videoUrl).apply {
						addOption("-f", "best")
					}

					getInstance().getInfo(request).let { info ->
						executeOnMainThread {
							waitingDialog.dialogBuilder?.let { dialogBuilder ->
								if (dialogBuilder.isShowing) {
									waitingDialog.close()

									info.url?.let { extractedUrl ->
										openMediaPlayerActivity(
											downloadDataModel!!.videoInfo!!,
											downloadDataModel!!.videoFormat!!,
											extractedUrl
										)
									}
								}
							}
						}
					}
				}

				animActivitySwipeLeft(safeMotherActivityRef)
			} else {
				this.close()
				val context = safeMotherActivityRef
				val destinationActivity = MediaPlayerActivity::class.java

				context.startActivity(Intent(context, destinationActivity).apply {
					flags = context.getSingleTopIntentFlags()
					downloadDataModel?.let {
						putExtra(INTENT_EXTRA_STREAM_URL, it.fileURL)
						putExtra(INTENT_EXTRA_STREAM_TITLE, it.fileName)
					}
				})

				animActivitySwipeLeft(context)
				this@ActiveTasksOptions.close()
			}
		}
	}

	@OptIn(UnstableApi::class)
	private fun openMediaPlayerActivity(
		videoInfo: VideoInfo,
		videoFormat: VideoFormat,
		streamableMediaUrl: String
	) {
		getSafeActivity()?.let { safeMotherActivityRef ->
			val activity = safeMotherActivityRef
			val playerClass = MediaPlayerActivity::class.java

			activity.startActivity(Intent(activity, playerClass).apply {
				flags = activity.getSingleTopIntentFlags()
				putExtra(INTENT_EXTRA_STREAM_URL, streamableMediaUrl)

				val selectedExtension = videoFormat.formatExtension
				val streamingTitle = "${videoInfo.videoTitle}.$selectedExtension"
				putExtra(INTENT_EXTRA_STREAM_TITLE, streamingTitle)
			})
		}
	}

	private fun resumeDownloadTask() {
		close()

		downloadDataModel?.let { model ->
			ThreadsUtility.executeInBackground(codeBlock = {
				if (downloadSystem.searchActiveDownloadTaskWith(model) != null) {
					ThreadsUtility.executeOnMain { pauseDownloadTask() }
					delay(1000)
				}

				ThreadsUtility.executeOnMain {
					val hasProblem = model.isYtdlpHavingProblem &&
						model.ytdlpProblemMsg.isNotEmpty() &&
						model.status != DOWNLOADING
					val isLoginIssue = model.ytdlpProblemMsg.contains("login", true)

					if (hasProblem && isLoginIssue) {
						showMessageDialog(
							baseActivityInf = getSafeActivity(),
							titleTextViewCustomize = {
								it.setText(string.title_login_required)
								getSafeActivity()?.getColor(R.color.color_error)
									?.let { colorResId -> it.setTextColor(colorResId) }
							},
							messageTextViewCustomize = {
								it.setText(string.text_login_to_download_private_videos)
							},
							isNegativeButtonVisible = false,
							positiveButtonTextCustomize = {
								it.setText(string.title_login_now)
								it.setLeftSideDrawable(R.drawable.ic_button_login)
							}
						)?.apply {
							setOnClickForPositiveButton {
								close()

								getSafeActivity()?.let { safeMotherActivityRef ->
									val browserFragment = safeMotherActivityRef.browserFragment
									val webviewEngine = browserFragment?.getBrowserWebEngine()
										?: return@setOnClickForPositiveButton

									val sideNavigation = safeMotherActivityRef.sideNavigation
									sideNavigation?.addNewBrowsingTab(model.siteReferrer, webviewEngine)
									safeMotherActivityRef.openBrowserFragment()

									model.isYtdlpHavingProblem = false
									model.ytdlpProblemMsg = ""
								}
							}
						}?.show()
					} else {
						downloadSystem.resumeDownload(
							downloadModel = model,
							coroutineScope = CoroutineScope(Dispatchers.IO),
							onResumed = {
								showToast(
									activityInf = getSafeActivity(),
									msgId = string.title_resumed_task_successfully
								)
							}
						)
					}
				}
			})
		}
	}

	private fun pauseDownloadTask() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			close()

			downloadDataModel?.let { downloadModel ->
				if (downloadSystem.searchActiveDownloadTaskWith(downloadModel) == null) {
					showToast(
						activityInf = safeMotherActivityRef,
						msgId = string.title_download_task_already_paused
					)
					return
				}

				if (!downloadModel.isResumeSupported) {
					getMessageDialog(
						baseActivityInf = safeMotherActivityRef,
						isNegativeButtonVisible = false,
						messageTextViewCustomize = {
							it.setText(string.text_warning_resume_not_supported)
						},
						negativeButtonTextCustomize = {
							it.setLeftSideDrawable(R.drawable.ic_button_cancel)
						},
						positiveButtonTextCustomize = {
							it.setText(string.title_pause_anyway)
							it.setLeftSideDrawable(R.drawable.ic_button_media_pause)
						}
					)?.apply {
						setOnClickForPositiveButton {
							this.close()
							dialogBuilder.close()
							downloadSystem.pauseDownload(downloadModel = downloadModel)
						}
						this.show()
					}
				} else {
					downloadSystem.pauseDownload(downloadModel = downloadModel)
				}
			}
		}
	}

	private fun removeDownloadTask() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			downloadDataModel?.let { downloadDataModel ->
				ThreadsUtility.executeInBackground(codeBlock = {
					if (downloadSystem.searchActiveDownloadTaskWith(downloadDataModel) != null) {
						ThreadsUtility.executeOnMain { pauseDownloadTask() }
						delay(1500)
					}

					ThreadsUtility.executeOnMain {
						val taskInf = downloadSystem.searchActiveDownloadTaskWith(downloadDataModel)
						if (taskInf != null) {
							showMessageDialog(
								baseActivityInf = safeMotherActivityRef,
								isNegativeButtonVisible = false,
								messageTextViewCustomize = {
									it.setText(string.text_cant_remove_one_active_download)
								},
								positiveButtonTextCustomize = {
									it.setLeftSideDrawable(R.drawable.ic_button_checked_circle)
								}
							)
							return@executeOnMain
						}
					}
				})
			}

			getMessageDialog(
				baseActivityInf = safeMotherActivityRef,
				isTitleVisible = true,
				isNegativeButtonVisible = false,
				titleTextViewCustomize = {
					it.setText(string.title_are_you_sure_about_this)
				},
				messageTextViewCustomize = {
					it.setText(string.text_are_you_sure_about_clear)
				},
				positiveButtonTextCustomize = {
					it.setText(string.title_clear_from_list)
					it.setLeftSideDrawable(R.drawable.ic_button_clear)
				}
			)?.apply {
				setOnClickForPositiveButton {
					close()
					this@ActiveTasksOptions.close()

					downloadDataModel?.let {
						downloadSystem.clearDownload(it) {
							executeOnMainThread {
								showToast(
									activityInf = safeMotherActivityRef,
									msgId = string.title_successfully_cleared
								)
							}
						}
					}
				}
				show()
			}
		}
	}

	private fun deleteDownloadTask() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			downloadDataModel?.let { downloadDataModel ->
				ThreadsUtility.executeInBackground(codeBlock = {
					if (downloadSystem.searchActiveDownloadTaskWith(downloadDataModel) != null) {
						ThreadsUtility.executeOnMain { pauseDownloadTask() }
						delay(1500)
					}

					ThreadsUtility.executeOnMain {
						val taskInf = downloadSystem.searchActiveDownloadTaskWith(downloadDataModel)
						if (taskInf != null) {
							showMessageDialog(
								baseActivityInf = safeMotherActivityRef,
								isNegativeButtonVisible = false,
								messageTextViewCustomize = {
									it.setText(string.text_cant_delete_on_active_download)
								},
								positiveButtonTextCustomize = {
									it.setLeftSideDrawable(R.drawable.ic_button_checked_circle)
								}
							)
							return@executeOnMain
						}
					}
				})
			}

			getMessageDialog(
				baseActivityInf = safeMotherActivityRef,
				titleTextViewCustomize = {
					it.setText(string.title_are_you_sure_about_this)
				},
				isTitleVisible = true,
				isNegativeButtonVisible = false,
				messageTextViewCustomize = {
					it.setText(string.text_are_you_sure_about_delete)
				},
				positiveButtonTextCustomize = {
					it.setText(string.title_delete_file)
					it.setLeftSideDrawable(R.drawable.ic_button_checked_circle)
				}
			)?.apply {
				setOnClickForPositiveButton {
					close()
					this@ActiveTasksOptions.close()

					downloadDataModel?.let {
						downloadSystem.deleteDownload(it) {
							executeOnMainThread {
								showToast(
									activityInf = safeMotherActivityRef,
									msgId = string.title_successfully_deleted
								)
							}
						}
					}
				}
				show()
			}
		}
	}

	private fun renameDownloadTask() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			if (!::downloadFileRenamer.isInitialized) {
				downloadFileRenamer = DownloadFileRenamer(
					motherActivity = safeMotherActivityRef,
					downloadDataModel = downloadDataModel!!
				) { dialogBuilder.close() }
			}

			downloadDataModel?.let { downloadDataModel ->
				val taskInf = downloadSystem.searchActiveDownloadTaskWith(downloadDataModel)
				if (taskInf != null) {
					showMessageDialog(
						baseActivityInf = safeMotherActivityRef,
						isNegativeButtonVisible = false,
						messageTextViewCustomize = {
							it.setText(string.text_cant_rename_on_active_download)
						},
						positiveButtonTextCustomize = {
							it.setLeftSideDrawable(R.drawable.ic_button_checked_circle)
						}
					)
					return
				}

				downloadFileRenamer.show(downloadDataModel)
			}
		}
	}

	private fun toggleDownloadThumbnail() {
		getSafeActivity()?.let { safeMotherActivity ->
			safeMotherActivity.doSomeVibration(50)
			showToast(
				activityInf = safeMotherActivity,
				msgId = string.title_experimental_feature
			)
		}
	}

	private fun copyDownloadFileLink() {
		downloadDataModel?.fileURL?.takeIf { isValidURL(it) }?.let { fileUrl ->
			copyTextToClipboard(getSafeActivity(), fileUrl)
			showToast(getSafeActivity(), string.title_file_url_has_been_copied)
			close()
		} ?: run {
			showToast(getSafeActivity(), string.title_dont_have_anything_to_copy)
		}
	}

	private fun copyWebsiteLink() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		dataModel.siteReferrer
			.takeIf { isValidURL(it) }
			?.let { fileUrl ->
				copyTextToClipboard(activityRef, fileUrl)
				showToast(activityRef, string.title_file_url_has_been_copied)
				close()
			} ?: run {
			showToast(activityRef, string.title_dont_have_anything_to_copy)
		}
	}

	private fun shareDownloadFileLink() {
		downloadDataModel?.fileURL?.takeIf { isValidURL(it) }?.let { fileUrl ->
			val titleText = getText(string.title_share_download_file_url)
			shareUrl(getSafeActivity(), fileUrl, titleText) {
				close()
			}
		} ?: run {
			showToast(getSafeActivity(), string.title_dont_have_anything_to_share)
		}
	}

	private fun openDownloadReferrerLink() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			val downloadSiteReferrerLink = downloadDataModel?.siteReferrer

			if (downloadSiteReferrerLink.isNullOrEmpty()) {
				safeMotherActivityRef.doSomeVibration(50)
				showToast(
					activityInf = safeMotherActivityRef,
					msgId = string.title_no_referer_link_found
				)
				return
			}

			this.close()
			this@ActiveTasksOptions.close()

			val webviewEngine = safeMotherActivityRef.browserFragment?.browserFragmentBody?.webviewEngine!!
			safeMotherActivityRef.sideNavigation?.addNewBrowsingTab(downloadSiteReferrerLink, webviewEngine)
			safeMotherActivityRef.openBrowserFragment()
		}
	}

	private fun openDownloadInfoTracker() {
		getSafeActivity()?.let { safeMotherActivityRef ->
			if (!::downloadInfoTracker.isInitialized) {
				downloadInfoTracker = DownloadInfoTracker(safeMotherActivityRef)
			}

			downloadInfoTracker.show(downloadDataModel!!)
			close()
		}
	}

	private fun openAdvancedDownloadSettings() {
		getSafeActivity()?.let { safeMotherActivity ->
			safeMotherActivity.doSomeVibration(50)
			showToast(
				activityInf = safeMotherActivity,
				msgId = string.title_experimental_feature
			)
		}
	}

	private fun downloadOtherYTResolutions() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		close()
		val fileUrl = dataModel.fileURL.ifEmpty { dataModel.siteReferrer }
		if (fileUrl.isEmpty()) return
		VideoLinkPasteEditor(
			motherActivity = activityRef,
			passOnUrl = fileUrl,
			autoStart = true
		).show()
	}

	private fun toggleWifiOnlyDownload() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		val settings = dataModel.globalSettings
		val isDownloadWifiOnly = settings.downloadWifiOnly
		settings.downloadWifiOnly = !isDownloadWifiOnly
		dataModel.updateInStorage()
		refreshToggleSwitchUI()
	}

	private fun toggleDownloadNotification() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		val settings = dataModel.globalSettings
		val isDownloadWifiOnly = settings.downloadHideNotification
		settings.downloadHideNotification = !isDownloadWifiOnly
		dataModel.updateInStorage()
		refreshToggleSwitchUI()
	}

	private fun toggleDownloadSound() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		val settings = dataModel.globalSettings
		val isDownloadWifiOnly = settings.downloadPlayNotificationSound
		settings.downloadPlayNotificationSound = !isDownloadWifiOnly
		dataModel.updateInStorage()
		refreshToggleSwitchUI()
	}

	private fun refreshToggleSwitchUI() {
		val dataModel = downloadDataModel
		val activityRef = getSafeActivity()

		if (activityRef == null) return
		if (dataModel == null) return

		val settings = dataModel.globalSettings
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
	}

	private fun setupDialogClickListeners() {
		dialogBuilder.setView(layout.frag_down_3_active_1_onclick_1).view.apply {
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
				R.id.btn_wifi_only_download to { toggleWifiOnlyDownload() },
				R.id.btn_download_notification to { toggleDownloadNotification() },
				R.id.btn_play_download_sound to { toggleDownloadSound() },
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