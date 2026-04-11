package app.ui.main.fragments.downloads.fragments.finished

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.view.View.*
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.downloadSystem
import app.core.bases.BaseActivity
import app.core.engines.downloader.AIODownload
import app.core.engines.downloader.AIODownload.Companion.ID_DOWNLOAD_KEY
import app.core.engines.downloader.AIODownload.Companion.THUMB_EXTENSION
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.video_parser.dialogs.VideoLinkPasteEditor
import app.core.engines.video_parser.parsers.SupportedURLs.isYouTubeUrl
import app.ui.main.fragments.downloads.dialogs.DownloadFileRenamer
import app.ui.main.fragments.downloads.dialogs.DownloadInfoTracker
import app.ui.others.media_player.MediaPlayerActivity
import app.ui.others.media_player.MediaPlayerActivity.Companion.INTENT_EXTRA_MEDIA_FILE_PATH
import app.ui.others.media_player.dialogs.Mp4ToAudioConverterDialog.showMp4ToAudioConverterDialog
import com.aio.R
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.ShareUtility.openApkFile
import lib.device.ShareUtility.openFile
import lib.device.ShareUtility.shareMediaFile
import lib.files.FileSystemUtility.endsWithExtension
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideoByName
import lib.files.VideoFilesUtility.moveMoovAtomToStart
import lib.networks.URLUtility.isValidURL
import lib.process.AsyncJobUtils.executeInBackground
import lib.process.AsyncJobUtils.executeOnMainThread
import lib.process.LogHelperUtils
import lib.texts.ClipboardUtils.copyTextToClipboard
import lib.texts.CommonTextUtils.getText
import lib.ui.ActivityAnimator.animActivityFade
import lib.ui.MsgDialogUtils
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.ViewUtility
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.normalizeTallSymbols
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

/**
 * Handles user actions for a completed download via a contextual bottom-sheet menu.
 *
 * This controller builds and displays a [DialogBuilder]-based action panel that allows
 * users to play media, open files externally (including APKs), share, rename, delete,
 * or clear downloads. It also supports moving files between private storage and the
 * system gallery, fixing unseekable MP4 files, converting video to audio, and viewing
 * technical download details and source links.
 *
 * Fragment and activity references are held using [WeakReference] to prevent memory leaks.
 *
 * @param finishedTasksFragment The parent [FinishedTasksFragment] managing completed downloads.
 */
class FinishedDownloadOptions(finishedTasksFragment: FinishedTasksFragment?) : OnClickListener {

	/**
	 * Logger instance used for tracking internal events, debugging, and error reporting
	 * within the [FinishedDownloadOptions] class.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the [FinishedTasksFragment] that initialized these options.
	 * Using a weak reference prevents memory leaks by allowing the fragment to be garbage
	 * collected even if this options handler remains in memory.
	 */
	private val weakReferenceOfFinishedFrag = WeakReference(finishedTasksFragment)

	/**
	 * A safe accessor for the [FinishedTasksFragment] that was passed during initialization.
	 *
	 * Since the fragment is held within a [WeakReference] to prevent memory leaks (e.g., when
	 * the fragment is destroyed but this options handler persists), this property attempts
	 * to retrieve the current instance.
	 *
	 * @return The [FinishedTasksFragment] instance if it is still in memory, or `null` if it
	 * has been garbage collected.
	 */
	private val safeFinishedTasksFragmentRef get() = weakReferenceOfFinishedFrag.get()

	/**
	 * A safe, nullable reference to the parent [BaseActivity] (the "mother" activity).
	 *
	 * This property is derived from the [safeFinishedTasksFragmentRef] and is used
	 * to safely perform UI operations, such as showing dialogs or starting activities,
	 * without directly holding a hard reference that could cause memory leaks.
	 */
	private val safeMotherActivityRef get() = safeFinishedTasksFragmentRef?.safeMotherActivityRef

	/**
	 * Builder responsible for constructing and managing the options dialog.
	 *
	 * It handles the initialization of the dialog's view, coordinates the display of
	 * download-specific metadata (thumbnails, titles, etc.), and manages the
	 * lifecycle of the dialog (show/close) within the fragment context.
	 */
	private var dialogBuilder: DialogBuilder? = null

	/**
	 * The data model representing the specific download item currently being managed.
	 *
	 * This model contains all relevant metadata for the download, including its file path,
	 * source URL, thumbnail information, and current status. It is used to populate the
	 * options dialog and provides the necessary context for actions like renaming,
	 * deleting, or playing the media.
	 */
	private var downloadDataModel: AIODownload? = null

	init {
		initialize()
	}

	/**
	 * Displays the download options dialog for a specific download item.
	 *
	 * Behavior:
	 * - Validates the availability of the parent fragment and activity.
	 * - Prevents multiple dialog instances by checking [isShowing].
	 * - Updates the internal [downloadDataModel] with the provided [dataModel].
	 * - Refreshes all dialog views (title, thumbnails, action buttons) to reflect the current file's metadata.
	 * - Launches the [dialogBuilder] to show the options UI to the user.
	 *
	 * @param dataModel The [AIODownload] containing the metadata and settings for the selected download.
	 */
	private fun initialize() {
		logger.d("Initializing FinishedDownloadOptions")
		dialogBuilder = DialogBuilder(safeMotherActivityRef)
		dialogBuilder?.setView(R.layout.frag_down_4_finish_1_onclick_1)

		dialogBuilder?.let { dialogBuilder ->
			ViewUtility.setViewOnClickListener(
				onClickListener = this,
				layout = dialogBuilder.view,
				ids = listOf(
					R.id.btn_file_info_card,
					R.id.btn_play_the_media,
					R.id.btn_open_download_file,
					R.id.btn_copy_site_link,
					R.id.btn_share_download_file,
					R.id.btn_clear_download,
					R.id.btn_delete_download,
					R.id.btn_rename_download,
					R.id.btn_open_website,
					R.id.btn_move_to_private,
					R.id.btn_remove_thumbnail,
					R.id.btn_fix_unseekable_mp4_file,
					R.id.btn_download_other_resolution,
					R.id.btn_mp4_to_mp3_convert,
					R.id.btn_download_system_information
				).toIntArray()
			)
			logger.d("Dialog builder initialized with click listeners")
		}
	}

	/**
	 * Displays the options dialog for a specific finished download.
	 *
	 * This function initiates the display sequence by:
	 * 1. Verifying that the parent fragment and activity references are still valid.
	 * 2. Checking if the dialog is already currently visible to prevent duplicate overlays.
	 * 3. Binding the provided [dataModel] to the dialog views (titles, thumbnails, action buttons).
	 * 4. Triggering the visual display of the [DialogBuilder].
	 *
	 * @param dataModel The [AIODownload] containing the metadata for the file to be managed.
	 */
	fun show(dataModel: AIODownload) {
		logger.d("Showing options dialog for download ID: ${dataModel.taskId}")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { _ ->
				dialogBuilder?.let { dialogBuilder ->
					if (!dialogBuilder.isShowing) {
						setDownloadModel(dataModel)
						updateDialogViewsWith(dataModel)
						dialogBuilder.show()
						logger.d("Options dialog shown successfully")
					} else {
						logger.d("Dialog is already showing, skipping show()")
					}
				}
			}
		}
	}

	/**
	 * Sets the download data model for this options provider and updates the internal reference.
	 *
	 * This model is used to populate the dialog views and provides the necessary metadata
	 * (file path, URL, ID) for actions like playing, sharing, or deleting the file.
	 *
	 * @param model The [AIODownload] containing the details of the finished download.
	 */
	fun setDownloadModel(model: AIODownload) {
		logger.d("Setting download model for ID: ${model.taskId}")
		this.downloadDataModel = model
	}

	/**
	 * Closes the options dialog if it is currently being displayed.
	 *
	 * This method checks if the [dialogBuilder] is active and showing,
	 * then triggers its internal close mechanism to dismiss the UI.
	 */
	fun close() {
		logger.d("Closing options dialog")
		dialogBuilder?.let { dialogBuilder ->
			if (dialogBuilder.isShowing) dialogBuilder.close()
		}
	}

	/**
	 * Handles click events for various option buttons in the finished downloads dialog.
	 *
	 * When a view is clicked, this function identifies the view by its ID and triggers
	 * the corresponding action such as playing media, opening files, sharing,
	 * renaming, deleting, or managing file privacy settings.
	 *
	 * @param view The [View] that was clicked, or null.
	 */
	override fun onClick(view: View?) {
		view?.let {
			logger.d("Option button clicked: ${view.id}")
			when (view.id) {
				R.id.btn_file_info_card -> playTheMedia()
				R.id.btn_play_the_media -> playTheMedia()
				R.id.btn_open_download_file -> openFile()
				R.id.btn_copy_site_link -> copySiteLink()
				R.id.btn_share_download_file -> shareFile()
				R.id.btn_clear_download -> clearFromList()
				R.id.btn_delete_download -> deleteFile()
				R.id.btn_rename_download -> renameFile()
				R.id.btn_open_website -> viewDownloadPage()
				R.id.btn_move_to_private -> toggleMoveToPrivateOrGallery()
				R.id.btn_remove_thumbnail -> toggleThumbnail()
				R.id.btn_fix_unseekable_mp4_file -> fixUnseekableMp4s()
				R.id.btn_download_other_resolution -> downloadOtherYTResolutions()
				R.id.btn_mp4_to_mp3_convert -> convertMp4ToAudio()
				R.id.btn_download_system_information -> showDownloadInfo()
			}
		}
	}

	/**
	 * Plays the media file associated with the download or opens it with the default system app.
	 *
	 * Behavior:
	 * - Checks if the downloaded file is a supported media type (audio or video).
	 *   - If **yes**: Launches [MediaPlayerActivity] for in-app playback.
	 *   - If **no**: Falls back to [openFile] to let the system handle the file type (e.g., PDFs, APKs).
	 *
	 * Implementation details:
	 * - Configures the [MediaPlayerActivity] intent with the following extras:
	 *   - [ID_DOWNLOAD_KEY]: The ID of the download for tracking/metadata purposes.
	 *   - [INTENT_EXTRA_MEDIA_FILE_PATH]: The absolute path to the local media file.
	 * - Uses [animActivityFade] to provide a smooth transition to the player.
	 * - Closes the options dialog immediately upon successful playback launch.
	 *
	 * ⚠ Requires `UnstableApi` opt-in due to usage of experimental Media3 APIs within the player transition.
	 */
	@OptIn(UnstableApi::class)
	fun playTheMedia() {
		logger.d("Play media option selected")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				dialogBuilder?.let { _ ->
					downloadDataModel?.let { downloadModel ->
						if (isMediaFile(downloadModel)) {
							logger.d("Starting MediaPlayerActivity for audio/video file")

							// Launch media player activity for audio/video
							safeMotherActivityRef.startActivity(
								Intent(
									safeMotherActivityRef,
									MediaPlayerActivity::class.java
								).apply {
									flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
									putExtra(ID_DOWNLOAD_KEY, downloadModel.taskId)
									putExtra(
										/* name = */ INTENT_EXTRA_MEDIA_FILE_PATH,
										/* value = */ downloadModel.getDestinationFile().path
									)
								}
							)

							// Apply fade animation and close dialog
							animActivityFade(safeMotherActivityRef)
							close()
						} else {
							logger.d("Non-media file, opening with default app")

							// Fallback: open file with system’s default handler
							openFile()
						}
					}
				}
			}
		}
	}

	/**
	 * Opens the downloaded file using the appropriate system application.
	 *
	 * Behavior:
	 * - Detects if the file is an APK based on its extension.
	 *   - If **yes**, triggers the APK installation flow using the app's FileProvider authority.
	 *   - If **no**, opens the file using the system's default activity for that file type.
	 * - Closes the options dialog before launching the external application.
	 *
	 * ⚠ APK files are handled via [openApkFile] to comply with Android security requirements.
	 */
	fun openFile() {
		logger.d("Open file option selected")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeActivityRef ->
				dialogBuilder?.let { _ ->
					close()
					val extensions = listOf("apk").toTypedArray()

					// Special case for APK files
					if (endsWithExtension(downloadDataModel!!.fileName, extensions)) {
						logger.d("APK file detected, opening with installation flow")
						// Special handling for APK files
						val authority = "${safeActivityRef.packageName}.provider"
						val apkFile = downloadDataModel!!.getDestinationFile()
						openApkFile(safeActivityRef, apkFile, authority)
					} else {
						logger.d("Opening non-APK file with default app")
						// Open other file types normally
						openFile(downloadDataModel!!.getDestinationFile(), safeActivityRef)
					}
				}
			}
		}
	}

	/**
	 * Shares the downloaded file with other applications.
	 *
	 * Behavior:
	 * - Closes the current options dialog before initiating the share.
	 * - Uses the system’s sharing intent mechanism via [shareMediaFile].
	 * - Allows the user to send the file to any supported application that can handle
	 *   the file's MIME type (e.g., messaging apps, email clients, or cloud storage).
	 */
	fun shareFile() {
		logger.d("Share file option selected")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				close()
				logger.d("Sharing media file")
				shareMediaFile(
					context = safeMotherActivityRef,
					file = downloadDataModel!!.getDestinationFile()
				)
			}
		}
	}

	/**
	 * Removes the download entry from the finished tasks list without deleting the actual file.
	 *
	 * Behavior:
	 * - Displays a confirmation dialog to the user.
	 * - If confirmed:
	 *   - Removes the [downloadDataModel] from the app's persistent database.
	 *   - Removes the item from the global in-memory finished downloads list.
	 *   - Closes the options dialog and provides success feedback via a toast message.
	 *
	 * ⚠ Note: The physical file remains in storage; only the record within the app is removed.
	 */
	fun clearFromList() {
		logger.d("Clear from list option selected")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->

				// Show confirmation dialog before clearing
				getMessageDialog(
					baseActivityInf = safeMotherActivityRef,
					isTitleVisible = true,
					isNegativeButtonVisible = false,
					titleTextViewCustomize = { it.setText(R.string.title_are_you_sure_about_this) },
					messageTextViewCustomize = { it.setText(R.string.text_are_you_sure_about_clear) },
					positiveButtonTextCustomize = {
						it.setText(R.string.title_clear_from_list)
						it.setLeftSideDrawable(R.drawable.ic_button_clear)
					}
				)?.apply {
					setOnClickForPositiveButton {
						logger.d("User confirmed clear from list")
						close()
						this@FinishedDownloadOptions.close()

						// Remove model reference from disk and list (but keep file intact)
						downloadDataModel?.deleteInDB()
						downloadSystem.finishedDownloadDataModels.remove(downloadDataModel!!)

						// Notify user of success
						showToast(safeMotherActivityRef, R.string.title_successfully_cleared)
					}
					show()
				}
			}
		}
	}

	/**
	 * Deletes the downloaded file from storage and removes it from the finished downloads list.
	 *
	 * Behavior:
	 * - Shows a confirmation dialog asking the user if they are sure about deleting the file.
	 * - If the user confirms:
	 *   - Closes the current dialog and options menu.
	 *   - Executes file deletion in a background thread to prevent UI freezing:
	 *     - Removes the file from disk.
	 *     - Deletes the associated [AIODownload] from storage.
	 *     - Updates the global finished downloads list by removing the entry.
	 *   - On success, shows a toast message confirming deletion.
	 *
	 * This ensures a smooth and safe deletion process while keeping the UI responsive.
	 */
	fun deleteFile() {
		logger.d("Delete file option selected")
		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->

				// Show confirmation dialog before deleting
				getMessageDialog(
					baseActivityInf = safeMotherActivityRef,
					isTitleVisible = true,
					isNegativeButtonVisible = false,
					titleTextViewCustomize = { it.setText(R.string.title_are_you_sure_about_this) },
					messageTextViewCustomize = { it.setText(R.string.text_are_you_sure_about_delete) },
					positiveButtonTextCustomize = {
						it.setText(R.string.title_delete_file)
						it.setLeftSideDrawable(R.drawable.ic_button_delete)
					}
				)?.apply {
					setOnClickForPositiveButton {
						logger.d("User confirmed file deletion")
						close()
						this@FinishedDownloadOptions.close()

						// Run deletion in background thread to avoid blocking UI
						executeInBackground {
							logger.d("Deleting file in background")
							downloadDataModel?.deleteInDB()
							downloadDataModel?.getDestinationFile()?.delete()
							downloadSystem.finishedDownloadDataModels.remove(downloadDataModel!!)

							// Show success toast on main thread after deletion
							executeOnMainThread {
								showToast(safeMotherActivityRef, R.string.title_successfully_deleted)
							}
						}
					}
					show()
				}
			}
		}
	}

	/**
	 * Shows a dialog that allows the user to rename the downloaded file.
	 *
	 * Behavior:
	 * - Initializes [DownloadFileRenamer] if not already created.
	 * - Displays a dialog with the current file name prefilled.
	 * - On successful rename:
	 *   - Closes the options dialog.
	 *   - Waits briefly, then refreshes the finished downloads list
	 *     to reflect the new name in the UI.
	 *
	 * This improves user experience by enabling file management directly
	 * from within the app without leaving the finished tasks screen.
	 */
	fun renameFile() {
		// Log the user action for debugging
		logger.d("Rename file option selected")

		// Ensure both fragment and activity references are available before proceeding
		safeFinishedTasksFragmentRef?.let { safeFinishedDownloadFragmentRef ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->

				// Initialize or reuse the DownloadFileRenamer dialog instance
				logger.d("Initializing DownloadFileRenamer for the first time")
				val downloadFileRenamer =
					DownloadFileRenamer(safeMotherActivityRef, downloadDataModel!!) {
						// Callback executed after a successful rename operation
						logger.d("File rename completed successfully")

						// Switch to the main thread to update UI components
						executeOnMainThread {
							// Notify the adapter to refresh the list items, reflecting the new filename
							safeFinishedDownloadFragmentRef
								.finishedTasksListAdapter
								?.notifyDataSetChangedOnSort(true)

							// Close the current options/menu dialog
							dialogBuilder?.close()
						}
					}

				// Ensure the dialog is operating on the latest data model before displaying
				logger.d("Showing rename dialog for download ID: ${downloadDataModel?.taskId}")
				downloadFileRenamer.downloadDataModel = downloadDataModel!!

				// Display the rename dialog to the user
				downloadFileRenamer.show(downloadDataModel!!)
			}
		}
	}

	/**
	 * Opens the source webpage associated with the current download in the in-app browser.
	 *
	 * Logic Flow:
	 * 1.  **Direct Referrer:** If a `siteReferrer` exists, it opens that link directly in a new tab.
	 * 2.  **Fallback Prompt:** If the referrer is missing but a `fileURL` exists, it prompts the
	 *     user via a dialog to open the direct download link instead.
	 * 3.  **Error State:** If neither link is available, it provides haptic feedback and displays
	 *     a warning dialog indicating no associated webpage was found.
	 *
	 * This allows users to "discover more" by returning to the original source where the
	 * download was initiated.
	 */
	fun viewDownloadPage() {
		logger.d("Discover more option selected")
		safeMotherActivityRef?.let { safeMotherActivityRef ->
			val siteReferrerLink = downloadDataModel!!.siteReferrer

			// Case 1: No referrer link at all
			if (siteReferrerLink.isEmpty()) {
				logger.d("No site referrer link available")
				close()
				safeMotherActivityRef.doSomeVibration(20)
				val msgTxt = getText(R.string.text_missing_webpage_link_info)
				MsgDialogUtils.showMessageDialog(
					baseActivityInf = safeMotherActivityRef,
					titleText = getText(R.string.title_missing_associate_webpage),
					isTitleVisible = true,
					messageTxt = msgTxt,
					isNegativeButtonVisible = false
				)
				return
			}

			val referrerLink = downloadDataModel?.siteReferrer
			val browserFragment = safeMotherActivityRef.browserFragment
			val webviewEngine = browserFragment?.browserFragmentBody?.webviewEngine!!

			// Case 2: Referrer link is null/empty -> fallback to download URL
			if (referrerLink.isNullOrEmpty()) {
				logger.d("Referrer link is null or empty, falling back to download URL")

				// Fallback to download URL if referrer is missing
				getMessageDialog(
					baseActivityInf = safeMotherActivityRef,
					isTitleVisible = true,
					titleText = getText(R.string.title_no_referral_site_added),
					messageTxt = getText(R.string.text_no_referrer_message_warning),
					positiveButtonText = getText(R.string.title_open_download_url),
					negativeButtonText = getText(R.string.title_cancel)
				)?.apply {
					setOnClickForPositiveButton {
						val fileUrl = downloadDataModel!!.fileURL
						logger.d("Opening download URL in browser: $fileUrl")
						this.close()
						this@FinishedDownloadOptions.close()
						safeMotherActivityRef.sideNavigation
							?.addNewBrowsingTab(fileUrl, webviewEngine)
						safeMotherActivityRef.openBrowserFragment()
					}
				}?.show()
				return
			}

			// Case 3: Valid referrer link available
			this.close()
			this@FinishedDownloadOptions.close()

			// Open the referrer link in browser
			logger.d("Opening referrer link in browser: $referrerLink")
			safeMotherActivityRef.sideNavigation
				?.addNewBrowsingTab(referrerLink, webviewEngine)
			safeMotherActivityRef.openBrowserFragment()
		}
	}

	/**
	 * Toggles the file's storage location between the app's private folder and the system gallery.
	 *
	 * This function determines the current storage state of the downloaded file and initiates
	 * a migration to the opposite location:
	 * - If stored in [PRIVATE_FOLDER] → Migrates the file to the public gallery via [moveToGallery].
	 * - If stored in the gallery → Migrates the file to the private app storage via [moveToPrivate].
	 *
	 * The toggle provides a convenient way for users to manage file visibility and security
	 * without manually navigating through storage settings.
	 */
	fun toggleMoveToPrivateOrGallery() {
		safeFinishedTasksFragmentRef?.let { safeFinishedFragmentRef ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				downloadDataModel?.let { downloadDataModel ->
					val globalSettings = downloadDataModel.config
					val downloadLocation = globalSettings.defaultDownloadLocationType
					if (downloadLocation == PRIVATE_FOLDER) moveToGallery() else moveToPrivate()
				}
			}
		}
	}

	/**
	 * Moves the downloaded file to the app-managed private storage.
	 *
	 * This function performs a migration of the file from its current location (typically the
	 * system gallery or a public folder) to a secure private directory. The process includes:
	 * - Showing a progress dialog during the migration to inform the user.
	 * - Executing the file move operation via the [AIODownload].
	 * - Updating the persistent storage and in-memory lists with the new file path.
	 * - Refreshing the UI components (Finished Downloads list and Home fragment) upon success.
	 * - Providing feedback via toasts for both successful migration and potential errors.
	 *
	 * @see moveToGallery for moving files back to public storage.
	 * @see toggleMoveToPrivateOrGallery for the automated toggle logic.
	 */
	fun moveToPrivate() {
		logger.d("moveToPrivate: Starting private storage migration")
		safeFinishedTasksFragmentRef?.let { safeFinishedFragmentRef ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				downloadDataModel?.let { downloadDataModel ->
					// Close the main dialog
					close()

					// Show progress dialog
					val waitingDialog = WaitingDialog(
						baseActivityInf = safeMotherActivityRef,
						loadingMessage = getText(R.string.title_moving_to_private_folder_wait),
						shouldHideOkayButton = true,
						isCancelable = false
					)
					waitingDialog.dialogBuilder?.setOnClickForPositiveButton { waitingDialog.close() }
					waitingDialog.show()

					// Execute file migration
					downloadDataModel.migrateToPrivateStorage(
						onError = {
							logger.e("moveToPrivate: Migration failed for ${downloadDataModel.fileName}")
							waitingDialog.close()
							showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
						},
						onSuccess = {
							logger.i(
								"moveToPrivate: Successfully migrated " +
									"${downloadDataModel.fileName} to private storage"
							)

							// Refresh UI
							safeFinishedFragmentRef.finishedTasksListAdapter?.notifyDataSetChangedOnSort(true)
							safeMotherActivityRef.homeFragment?.refreshRecentDownloadListUI()

							waitingDialog.close()
							showToast(safeMotherActivityRef, R.string.title_move_to_private_successfully)
						}
					)

				} ?: logger.d("moveToPrivate: No download model available")
			} ?: logger.d("moveToPrivate: No activity reference")
		} ?: logger.d("moveToPrivate: No fragment reference")
	}

	/**
	 * Moves the file from private storage to the system gallery for public access.
	 *
	 * This function handles the migration of downloaded files from app-private storage
	 * to the public gallery where they become visible to other apps and the user's media library.
	 *
	 * The process includes:
	 * - Showing a progress dialog during the migration.
	 * - Handling success/error cases with appropriate user feedback.
	 * - Refreshing the UI to reflect the new file location.
	 * - Updating both finished tasks list and recent downloads display.
	 *
	 * @see moveToPrivate for the reverse operation.
	 */
	fun moveToGallery() {
		logger.d("moveToGallery: Starting gallery migration")
		safeFinishedTasksFragmentRef?.let { safeFinishedFragmentRef ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				downloadDataModel?.let { downloadDataModel ->
					// Close the main dialog
					close()

					// Show progress dialog
					val waitingDialog = WaitingDialog(
						baseActivityInf = safeMotherActivityRef,
						loadingMessage = getText(R.string.title_moving_to_gallery_folder_wait),
						shouldHideOkayButton = true,
						isCancelable = false
					)
					waitingDialog.dialogBuilder?.setOnClickForPositiveButton { waitingDialog.close() }
					waitingDialog.show()

					// Execute file migration
					downloadDataModel.moveToSysGalleryFolder(
						onError = {
							logger.e("moveToGallery: Migration failed for ${downloadDataModel.fileName}")
							waitingDialog.close()
							showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
						},
						onSuccess = {
							logger.i(
								"moveToGallery: Successfully migrated " +
									"${downloadDataModel.fileName} to gallery"
							)

							// Refresh UI
							safeFinishedFragmentRef.finishedTasksListAdapter?.notifyDataSetChangedOnSort(true)
							safeMotherActivityRef.homeFragment?.refreshRecentDownloadListUI()

							waitingDialog.close()
							showToast(safeMotherActivityRef, R.string.title_move_to_gallery_successfully)
						}
					)

				} ?: logger.d("moveToGallery: No download model available")
			} ?: logger.d("moveToGallery: No activity reference")
		} ?: logger.d("moveToGallery: No fragment reference")
	}

	/**
	 * Toggles the visibility of video thumbnails for the current download item.
	 *
	 * This function:
	 * - Flips the `downloadHideVideoThumbnail` flag in the global settings associated with the download.
	 * - Persists the updated configuration to local storage.
	 * - Refreshes the finished tasks list and recent downloads UI to reflect the change immediately.
	 * - Provides error handling with a toast message if the update process fails.
	 *
	 * When disabled, the UI will display a generic file-type icon instead of a generated video frame.
	 */
	fun toggleThumbnail() {
		logger.d("Remove thumbnail option selected")
		safeFinishedTasksFragmentRef?.let { finishedFragment ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				close()
				try {
					logger.d("Toggling thumbnail visibility setting")

					val downloadConfig = downloadDataModel?.config
					downloadConfig?.downloadHideVideoThumbnail =
						!downloadConfig.downloadHideVideoThumbnail

					// Persist updated model
					downloadDataModel?.updateInDB()

					// Notify adapter to refresh list with updated state
					val finishedTasksListAdapter = finishedFragment.finishedTasksListAdapter
					finishedTasksListAdapter?.notifyDataSetChangedOnSort(true)

					logger.d("Thumbnail visibility toggled successfully")
					safeMotherActivityRef.homeFragment?.refreshRecentDownloadListUI()
				} catch (error: Exception) {
					logger.e("Error found at hide/show thumbnail -", error)
					showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	/**
	 * Attempts to fix MP4 files that are unseekable by moving the `moov` atom from the end of the
	 * file to the beginning (Fast Start).
	 *
	 * This method performs the following steps:
	 * 1.  Displays a warning dialog to the user, as atom manipulation is a risky operation.
	 * 2.  Validates the existence of the destination file upon user confirmation.
	 * 3.  Initiates a background thread to process the file using [moveMoovAtomToStart].
	 * 4.  Provides UI feedback via a [WaitingDialog] during processing and a Toast upon completion.
	 *
	 * @throws Exception if the file is inaccessible or the `moov` atom cannot be relocated.
	 * @see lib.files.VideoFilesUtility.moveMoovAtomToStart
	 */
	fun fixUnseekableMp4s() {
		logger.d("Fix unseekable MP4 option selected")

		// Ensure we have a reference to the parent activity before proceeding
		safeMotherActivityRef?.let { safeMotherActivityRef ->
			// Show a confirmation dialog to the user
			getMessageDialog(
				baseActivityInf = safeMotherActivityRef,
				isNegativeButtonVisible = false, // Only one confirmation option
				isTitleVisible = true,
				titleTextViewCustomize = {
					val resources = safeMotherActivityRef.resources
					val errorColor = resources.getColor(R.color.color_error, null)
					it.setTextColor(errorColor)
					it.text = getText(R.string.title_are_you_sure_about_this)
				},
				messageTextViewCustomize = {
					it.setText(R.string.text_msg_of_fixing_unseekable_mp4_files)
				},
				positiveButtonTextCustomize = {
					it.setLeftSideDrawable(R.drawable.ic_button_fix_hand)
					it.setText(R.string.title_proceed_anyway)
				}
			)?.apply {
				// If the user clicks "Proceed anyway"
				setOnClickForPositiveButton {
					this.close()

					val destinationFile = downloadDataModel?.getDestinationFile()
					// Validate file existence before continuing
					if (destinationFile == null || destinationFile.exists() == false) {
						safeMotherActivityRef.doSomeVibration(50)
						showToast(
							activityInf = safeMotherActivityRef,
							msgId = R.string.title_something_went_wrong
						)
						return@setOnClickForPositiveButton
					}

					// Show a loading/waiting dialog while processing
					val waitingDialog = WaitingDialog(
						baseActivityInf = safeMotherActivityRef,
						loadingMessage = getText(R.string.title_fixing_mp4_file_please_wait),
						isCancelable = false,
						shouldHideOkayButton = true
					)

					// Run the actual fixing process on a background thread
					safeMotherActivityRef.getAttachedCoroutineScope().launch(Dispatchers.IO) {
						try {
							// Show waiting dialog on UI thread
							withContext(Dispatchers.Main) { waitingDialog.show() }

							// Attempt to move moov atom to start in-place
							moveMoovAtomToStart(destinationFile, destinationFile)

							// Notify success back on the UI thread
							withContext(Dispatchers.Main) {
								showToast(safeMotherActivityRef, R.string.title_fixing_mp4_done_successfully)
								waitingDialog.close()
							}
						} catch (error: Exception) {
							// Log and clean up on failure
							logger.e("Error in fixing unseekable mp4 file:", error)
							withContext(Dispatchers.Main) { waitingDialog.close() }
						}
					}
				}
			}?.show()
		}
	}

	/**
	 * Displays detailed technical and system information about the current download.
	 *
	 * Behavior:
	 * - Closes the current options dialog to focus the user's attention on the information view.
	 * - Lazily initializes the [DownloadInfoTracker] if it has not been created yet.
	 * - Triggers the tracker to display a comprehensive breakdown of the [downloadDataModel],
	 *   including file paths, download IDs, and other low-level metadata.
	 */
	fun showDownloadInfo() {
		logger.d("Download info option selected")

		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				// Close the current context/dialog before showing info
				close()

				// Initialize the download info tracker dialog
				logger.d("Initializing DownloadInfoTracker for the first time")
				val downloadInfoTracker = DownloadInfoTracker(safeMotherActivityRef)

				// Show detailed info about the current download
				logger.d("Showing download info for ID: ${downloadDataModel?.taskId}")
				downloadInfoTracker.show(downloadDataModel!!)
			}
		}
	}

	/**
	 * Initiates the download of the current video in different available resolutions.
	 *
	 * This function:
	 * - Closes the current options dialog to transition to the download flow.
	 * - Extracts the video's original source URL (site referrer).
	 * - Utilizes the browser's auto-search/detection engine to find alternative
	 *   formats or quality options for the video.
	 * - Navigates the user back to the browser fragment where the resolution
	 *   selection interface is presented.
	 *
	 * Note: This feature is specifically designed for YouTube or similar video
	 * platforms where multiple quality streams are available for the same content.
	 */
	fun downloadOtherYTResolutions() {
		safeMotherActivityRef?.let { activityRef ->
			downloadDataModel?.let { dataModel ->
				if (isYouTubeUrl(dataModel.fileURL)) {
					close()
					VideoLinkPasteEditor(
						baseActivity = activityRef,
						passOnUrl = dataModel.fileURL,
						autoStart = true
					).show()
				}
			}
		}
	}

	/**
	 * Initiates the conversion process of the current MP4 video into an audio file.
	 *
	 * This function displays a conversion dialog that allows the user to configure settings
	 * (such as output format or metadata) and start the extraction process. It utilizes
	 * the current [downloadDataModel] to retrieve the source file details and the
	 * [safeMotherActivityRef] to provide the UI context for the dialog.
	 *
	 * @see showMp4ToAudioConverterDialog
	 */
	fun convertMp4ToAudio() {
		close()
		showMp4ToAudioConverterDialog(safeMotherActivityRef, downloadDataModel)
	}

	/**
	 * Updates the dialog's title, thumbnail, and action views with details from the given download model.
	 *
	 * Behavior:
	 * - Updates title and subtitle (file name & URL).
	 * - Handles thumbnail display:
	 *   - Loads video/APK thumbnails if enabled in settings.
	 *   - Falls back to default thumbnail if disabled or unavailable.
	 * - Updates favicon display with site info.
	 * - Toggles thumbnail visibility text based on user settings.
	 * - Updates action buttons (play, convert, fix) depending on file type (audio/video/other).
	 * - Shows media playback duration and play indicator for media files only.
	 *
	 * Threading:
	 * - Runs thumbnail updates asynchronously when needed.
	 * - UI updates always executed on the main thread.
	 *
	 * Error handling:
	 * - Falls back to default icons/drawables when thumbnails or metadata are missing.
	 *
	 * @param downloadModel The [AIODownload] containing the download's metadata and settings.
	 */
	private fun updateDialogViewsWith(downloadModel: AIODownload) {
		logger.d("Updating title and thumbnails for download ID: ${downloadModel.taskId}")

		dialogBuilder?.let { dialogBuilder ->
			dialogBuilder.view.apply {
				// UI References - Get all view components from the dialog layout
				val txtFileUrlSubTitle = findViewById<TextView>(R.id.txt_file_url)
				val txtFileNameTitle = findViewById<TextView>(R.id.txt_file_title)
				val txtPlayTheFile = findViewById<TextView>(R.id.txt_play_the_media)
				val imgFileThumbnail = findViewById<ImageView>(R.id.img_file_thumbnail)
				val imgFileFavicon = findViewById<ImageView>(R.id.img_site_favicon)
				val btnToggleThumbnail = findViewById<TextView>(R.id.txt_remove_thumbnail)
				val txtMoveToPrivate = findViewById<TextView>(R.id.txt_move_to_private)
				val btnConvertMp4ToAudio = findViewById<View>(R.id.btn_mp4_to_mp3_convert)
				val btnFixUnseekableMp4VideoFiles = findViewById<View>(R.id.container_mp4_file_fix)
				val containerDownloadYtRes = findViewById<View>(R.id.container_another_res_download)
				val containerMediaDuration = findViewById<View>(R.id.container_media_duration)
				val txtMediaPlaybackDuration = findViewById<TextView>(R.id.txt_media_duration)
				val imgMediaPlayIndicator = findViewById<View>(R.id.img_media_play_indicator)
				val imgFileTypeIndicator = findViewById<ImageView>(R.id.img_file_type_indicator)
				val imgPrivateFolderIndicator = findViewById<ImageView>(R.id.img_private_folder_indicator)

				// Title and subtitle - Update with file metadata
				txtFileNameTitle.isSelected = true // Enable marquee for long file names
				txtFileNameTitle.text = downloadModel.fileName
				txtFileUrlSubTitle.text = downloadModel.fileURL
				txtFileNameTitle.normalizeTallSymbols()

				// Thumbnail handling - Show/hide based on user settings
				imgFileThumbnail.apply {
					if (!downloadModel.config.downloadHideVideoThumbnail) {
						logger.d("thumbnails are enabled, updating thumbnail")
						updateThumbnail(this, downloadModel) // Load actual thumbnail asynchronously
					} else {
						logger.d("thumbnails are disabled, using default thumbnail")
						val defaultThumb = downloadModel.getThumbnailDrawableID()
						setImageResource(defaultThumb) // Use generic file type icon
					}
				}

				// Update favicon (site icon) - Show originating website identity
				updateFaviconInfo(downloadModel, imgFileFavicon)

				// Update private folder indication - Show storage location status
				updatePrivateFolderIndicator(downloadModel, imgPrivateFolderIndicator)

				// Thumbnail toggle button text - Dynamic text based on current state
				btnToggleThumbnail.apply {
					val isThumbnailHidden = downloadModel.config.downloadHideVideoThumbnail
					val drawableResIdRes = if (isThumbnailHidden) {
						R.drawable.ic_button_unchecked_circle_small
					} else R.drawable.ic_button_checked_circle_small
					setRightSideDrawable(drawableResIdRes, true)
				}

				// Play action text (based on file type) - Context-aware button labeling
				txtPlayTheFile.text = when {
					isAudioByName(downloadModel.fileName) -> getText(R.string.title_play_the_audio)
					isVideoByName(downloadModel.fileName) -> getText(R.string.title_play_the_video)
					else -> getText(R.string.title_open_the_file) // Generic for documents, images, etc.
				}

				// Move to private/gallery button - Toggle based on current storage location
				val globalSettings = downloadModel.config
				val downloadLocation = globalSettings.defaultDownloadLocationType
				if (downloadLocation == PRIVATE_FOLDER) {
					// Currently in private storage → show option to move to gallery
					txtMoveToPrivate.setLeftSideDrawable(R.drawable.ic_button_folder)
					txtMoveToPrivate.text = getText(R.string.title_move_to_gallery)
				} else {
					// Currently in gallery → show option to move to private storage
					txtMoveToPrivate.setLeftSideDrawable(R.drawable.ic_button_private_folder)
					txtMoveToPrivate.text = getText(R.string.title_move_to_private)
				}

				// Media-specific controls - Show/hide media-related UI elements
				if (isMediaFile(downloadModel)) {
					logger.d("Media file detected, showing all related view containers")

					// Show convert/fix buttons only for video files
					btnConvertMp4ToAudio.visibility =
						if (isVideoByName(downloadModel.fileName)) VISIBLE else GONE
					btnFixUnseekableMp4VideoFiles.visibility =
						if (isVideoByName(downloadModel.fileName)) VISIBLE else GONE

					// Show play indicator for media files
					imgMediaPlayIndicator.apply { showView(this, true) }

					// Show playback duration if available (for audio/video files)
					containerMediaDuration.apply {
						val mediaFilePlaybackDuration = downloadModel.mediaFilePlaybackDuration
						val playbackTimeString = mediaFilePlaybackDuration.replace("(", "").replace(")", "")
						if (playbackTimeString.isNotEmpty()) {
							showView(this, true)
							showView(txtMediaPlaybackDuration, true)
							txtMediaPlaybackDuration.text = playbackTimeString
						}
					}
				} else {
					// Non-media files → hide media-only views to reduce UI clutter
					logger.d("Non-media file, hiding duration container")
					containerMediaDuration.visibility = GONE
					imgMediaPlayIndicator.visibility = GONE
					btnConvertMp4ToAudio.visibility = GONE
					btnFixUnseekableMp4VideoFiles.visibility = GONE
				}

				// File type indicator - Show appropriate icon based on file extension
				logger.d("Updating file type indicator image view")
				imgFileTypeIndicator.setImageResource(
					when {
						isImageByName(downloadModel.fileName) -> R.drawable.ic_button_images
						isAudioByName(downloadModel.fileName) -> R.drawable.ic_button_audio
						isVideoByName(downloadModel.fileName) -> R.drawable.ic_button_video
						isDocumentByName(downloadModel.fileName) -> R.drawable.ic_button_document
						isArchiveByName(downloadModel.fileName) -> R.drawable.ic_button_archives
						isProgramByName(downloadModel.fileName) -> R.drawable.ic_button_programs
						else -> R.drawable.ic_button_file // Default fallback icon
					}
				)

				// YouTube resolution options - Show/hide based on file type
				val link = downloadModel.siteReferrer.ifEmpty { downloadModel.fileURL }
				val visibility = if (link.isNotEmpty() && isYouTubeUrl(link)) VISIBLE else GONE
				containerDownloadYtRes.visibility = visibility
			}
		}
	}

	/**
	 * Loads and displays the website favicon for the specified download item.
	 *
	 * Logic:
	 * - Checks [isThumbnailRestricted] to determine if media-related imagery should be hidden.
	 * - Attempts to retrieve the favicon file path from the cache via [aioFavicons].
	 * - If a cached file exists, it loads the URI into the [imgFavicon] on the main thread.
	 * - If the favicon is missing, restricted, or an error occurs during loading, it falls back
	 *   to a default placeholder ([R.drawable.ic_image_default_favicon]).
	 *
	 * @param downloadDataModel The model containing the [siteReferrer] used to identify the favicon.
	 * @param imgFavicon The [ImageView] where the favicon or default icon will be displayed.
	 */
	private fun updateFaviconInfo(downloadDataModel: AIODownload, imgFavicon: ImageView) {
		logger.d("Updating favicon for download ID: ${downloadDataModel.taskId}")
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		val defaultFaviconDrawable = getDrawable(INSTANCE.resources, defaultFaviconResId, null)

		// Skip favicon loading if video thumbnails are not allowed
		if (isThumbnailRestricted(downloadDataModel)) {
			logger.d("Video thumbnails not allowed, using default favicon")
			executeOnMainThread {
				Glide.with(imgFavicon)
					.load(defaultFaviconResId)
					.into(imgFavicon)
			}
			return
		}

		// Load favicon in background thread
		safeMotherActivityRef?.getAttachedCoroutineScope()?.launch(Dispatchers.IO) {
			try {
				val referralSite = downloadDataModel.siteReferrer
				logger.d("Loading favicon for site: $referralSite")

				// Attempt to load favicon from cache
				aioFavicons.getFavicon(referralSite)?.let { faviconFilePath ->
					val faviconImgFile = File(faviconFilePath)
					if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
						logger.d("Favicon file not found")
						return@launch
					}

					// Load favicon URI on main thread
					val faviconImgURI = faviconImgFile.toUri()
					withContext(Dispatchers.Main) {
						try {
							logger.d("Setting favicon from URI")
							showView(imgFavicon, true)
							imgFavicon.setImageURI(faviconImgURI)
						} catch (error: Exception) {
							logger.e("Error setting favicon: ${error.message}", error)
							showView(imgFavicon, true)
							imgFavicon.setImageResource(defaultFaviconResId)
						}
					}
				}
			} catch (error: Exception) {
				logger.e("Error loading favicon: ${error.message}", error)
				imgFavicon.setImageDrawable(defaultFaviconDrawable)
			}
		}
	}

	/**
	 * Updates the visual indicator for the private folder status.
	 *
	 * This function sets the appropriate icon on the [privateFolderImageView] based on
	 * the download's storage location. If the file is stored in the [PRIVATE_FOLDER],
	 * a lock icon is displayed; otherwise, a standard folder icon is used.
	 *
	 * @param downloadModel The model containing the download's current storage settings.
	 * @param privateFolderImageView The [ImageView] where the status icon will be applied.
	 */
	private fun updatePrivateFolderIndicator(
		downloadModel: AIODownload,
		privateFolderImageView: ImageView
	) {
		logger.d("Updating private folder indicator UI state")
		val downloadLocation = downloadModel.config.defaultDownloadLocationType
		logger.d("Current download location: $downloadLocation")

		// Update indicator icon based on folder type
		privateFolderImageView.setImageResource(
			when (downloadLocation) {
				PRIVATE_FOLDER -> R.drawable.ic_button_lock  // Private folder
				else -> R.drawable.ic_button_folder         // Normal folder
			}
		)
	}

	/**
	 * Updates the thumbnail image for a given download.
	 *
	 * Behavior:
	 * 1. Attempts to load an APK-specific thumbnail first (if the file is an APK).
	 * 2. If not an APK, tries to use a cached thumbnail path if available.
	 * 3. If no cache exists, generates a new thumbnail from the video file or its URL.
	 * 4. Rotates portrait thumbnails into landscape orientation for consistent display.
	 * 5. Saves newly generated thumbnails for caching to avoid regenerating later.
	 *
	 * Threading:
	 * - Runs heavy thumbnail loading and generation in a background thread.
	 * - Switches back to the main thread for UI updates (to avoid freezing the UI).
	 *
	 * Error handling:
	 * - Falls back to the default thumbnail if loading/generation fails.
	 *
	 * @param thumbImageView The [ImageView] where the thumbnail should be displayed.
	 * @param downloadModel The [AIODownload] containing file and metadata for thumbnail handling.
	 */
	private fun updateThumbnail(thumbImageView: ImageView, downloadModel: AIODownload) {
		safeMotherActivityRef?.getAttachedCoroutineScope()?.launch(Dispatchers.IO) {
			logger.d("Updating thumbnail for download ID: ${downloadModel.taskId}")

			val destinationFile = downloadModel.getDestinationFile()
			val defaultThumb = downloadModel.getThumbnailDrawableID()
			val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)

			// Step 1: Try loading an APK thumbnail if this is an APK file
			if (loadApkThumbnail(downloadModel, thumbImageView, defaultThumbDrawable)) {
				logger.d("APK thumbnail loaded successfully")
				return@launch
			}

			// Step 2: If a cached thumbnail already exists, use it directly
			logger.d("Loading thumbnail in background thread")

			val cachedThumbPath = downloadModel.thumbPath
			if (cachedThumbPath.isNotEmpty()) {
				logger.d("Using cached thumbnail at: $cachedThumbPath")
				withContext(Dispatchers.Main) {
					loadBitmapWithGlide(
						imageView = thumbImageView,
						activity = safeMotherActivityRef,
						filePath = cachedThumbPath,
						defaultThumb = defaultThumb
					)
				}
				return@launch
			}

			// Step 3: No cache found, generate a new thumbnail
			logger.d("Generating new thumbnail from file/URL")
			val thumbnailUrl = downloadModel.videoInfo?.videoThumbnailUrl
			val bitmap = getThumbnailFromFile(destinationFile, thumbnailUrl, 420)

			if (bitmap != null) {
				// Step 4: Rotate portrait images for consistent display
				val isPortrait = bitmap.height > bitmap.width
				val rotatedBitmap = if (isPortrait) {
					logger.d("Rotating portrait thumbnail to landscape")
					rotateBitmap(bitmap, 270f)
				} else bitmap

				// Step 5: Save thumbnail to cache and update the model
				val thumbnailName = "${downloadModel.taskId}$THUMB_EXTENSION"
				saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
					logger.d("Saved thumbnail to cache: $filePath")
					downloadModel.thumbPath = filePath
					downloadModel.updateInDB()

					// Apply the new thumbnail on the main thread
					withContext(Dispatchers.Main) {
						loadBitmapWithGlide(
							imageView = thumbImageView,
							activity = safeMotherActivityRef,
							filePath = filePath,
							defaultThumb = defaultThumb
						)
					}
				}
			} else {
				logger.d("Failed to generate thumbnail from file/URL")
			}
		}
	}

	/**
	 * Determines whether the thumbnail for a specific download should be restricted from display.
	 *
	 * This check is primarily governed by the user's global privacy/display settings.
	 * If the `downloadHideVideoThumbnail` setting is enabled, thumbnails will be hidden
	 * to conserve space or maintain privacy.
	 *
	 * @param downloadDataModel The download data model containing the global settings to check.
	 * @return `true` if the thumbnail should be restricted (hidden), `false` otherwise.
	 */
	private fun isThumbnailRestricted(downloadDataModel: AIODownload): Boolean {
		return downloadDataModel.config.downloadHideVideoThumbnail
	}

	/**
	 * Loads the icon of an APK file to use it as the file's thumbnail.
	 *
	 * Behavior:
	 * - Verifies that the downloaded file exists and is an `.apk`.
	 * - Uses [PackageManager] to extract the APK's [ApplicationInfo] and load its icon.
	 * - Falls back to a default thumbnail if:
	 *   - The file is missing, not an APK, or corrupted.
	 *   - The APK has no package info or the icon fails to load.
	 *
	 * Error handling:
	 * - If any exception occurs, logs the error and resets the [ImageView] with
	 *   the default thumbnail and safe scaling.
	 *
	 * @param downloadModel The download data model containing the file reference.
	 * @param imageViewHolder The [ImageView] where the APK's icon or fallback thumbnail will be displayed.
	 * @param defaultThumbDrawable The default thumbnail drawable used when the APK icon is unavailable.
	 * @return `true` if the APK icon was successfully loaded, otherwise `false`.
	 */
	private suspend fun loadApkThumbnail(
		downloadModel: AIODownload,
		imageViewHolder: ImageView,
		defaultThumbDrawable: Drawable?
	): Boolean {
		logger.d("Attempting to load APK thumbnail for download ID: ${downloadModel.taskId}")

		safeFinishedTasksFragmentRef?.let { _ ->
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				dialogBuilder?.let { _ ->
					val apkFile = downloadModel.getDestinationFile()

					// Validate file existence and extension
					if (!apkFile.exists() || !apkFile.name.lowercase().endsWith(".apk")) {
						logger.d("File is not an APK or doesn't exist, using default thumbnail")
						withContext(Dispatchers.Main) {
							Glide.with(imageViewHolder)
								.load(defaultThumbDrawable)
								.into(imageViewHolder)
						}
						return false
					}

					val packageManager: PackageManager = safeMotherActivityRef.packageManager
					return try {
						logger.d("Loading APK package info")

						// Extract package info from the APK archive
						val packageInfo: PackageInfo? = packageManager.getPackageArchiveInfo(
							apkFile.absolutePath, PackageManager.GET_ACTIVITIES
						)

						packageInfo?.applicationInfo?.let { appInfo ->
							logger.d("APK package info found, loading icon")

							// Point ApplicationInfo to the APK file
							appInfo.sourceDir = apkFile.absolutePath
							appInfo.publicSourceDir = apkFile.absolutePath

							// Load APK icon into the ImageView
							val appIcon: Drawable = appInfo.loadIcon(packageManager)
							withContext(Dispatchers.Main) {
								Glide.with(imageViewHolder)
									.load(appIcon)
									.into(imageViewHolder)
							}
							true
						} ?: run {
							logger.d("No package info found for APK, using default thumbnail")
							withContext(Dispatchers.Main) {
								Glide.with(imageViewHolder)
									.load(defaultThumbDrawable)
									.into(imageViewHolder)
							}
							false
						}
					} catch (error: Exception) {
						logger.e("Error loading APK thumbnail: ${error.message}", error)

						// Fallback: reset ImageView to safe defaults with the fallback thumbnail
						imageViewHolder.apply {
							scaleType = ImageView.ScaleType.FIT_CENTER
							setPadding(0, 0, 0, 0)
							withContext(Dispatchers.Main) {
								Glide.with(this@apply)
									.load(defaultThumbDrawable)
									.into(this@apply)
							}
						}
						false
					}
				}
			}
		}
		return false
	}

	/**
	 * Asynchronously loads an image from a file path into an [ImageView] using Glide.
	 *
	 * This method ensures the image is loaded on the main thread and includes cache
	 * invalidation logic based on the file's last modified timestamp to ensure
	 * updated thumbnails are correctly displayed.
	 *
	 * Implementation details:
	 * - Validates that both the [imageView] and [activity] context are available.
	 * - Uses [ObjectKey] with the file's last modified timestamp as a signature to
	 *   bypass stale Glide cache.
	 * - Sets a placeholder while the image is loading.
	 * - Falls back to the [defaultThumb] resource ID if an exception occurs or the
	 *   file cannot be processed.
	 *
	 * @param imageView The target [ImageView] where the image will be loaded.
	 * @param activity The [BaseActivity] context used for the Glide lifecycle.
	 * @param filePath The absolute filesystem path of the image to load.
	 * @param defaultThumb The fallback drawable resource ID used in case of error.
	 */
	private suspend fun loadBitmapWithGlide(
		imageView: ImageView?,
		activity: BaseActivity?,
		filePath: String,
		defaultThumb: Int
	) {
		if (imageView == null || activity == null) return
		withContext(Dispatchers.Main) {
			try {
				if (imageView.context != activity) return@withContext
				val imgURI = File(filePath).toUri()
				val lastModified = File(filePath).lastModified()
				Glide.with(activity)
					.load(imgURI)
					.signature(ObjectKey(lastModified))
					.placeholder(R.drawable.image_no_thumb_available)
					.into(imageView)
			} catch (error: Exception) {
				logger.e(
					"Error loading thumbnail with " +
						"Glide: ${error.message}", error
				)
				imageView.setImageResource(defaultThumb)
			}
		}
	}

	/**
	 * Determines whether the downloaded file is a supported media type (audio or video).
	 *
	 * This helper function checks the file extension via its name to categorize it
	 * as a media file, which is used to toggle media-specific UI controls like
	 * playback indicators, duration labels, and conversion options.
	 *
	 * @param downloadModel The [AIODownload] representing the downloaded file.
	 * @return `true` if the file is identified as audio or video; otherwise `false`.
	 */
	private fun isMediaFile(downloadModel: AIODownload): Boolean {
		return isAudioByName(downloadModel.fileName) || isVideoByName(downloadModel.fileName)
	}

	/**
	 * Copies the source website link (site referrer) to the clipboard.
	 *
	 * Behavior:
	 * - Extracts the [AIODownload.siteReferrer] from the current download.
	 * - Validates the URL to ensure it is not empty and follows a correct format.
	 * - If valid:
	 *   - Copies the link to the system clipboard.
	 *   - Displays a success toast notification.
	 *   - Closes the options dialog.
	 * - If invalid or missing:
	 *   - Displays a warning toast indicating there is nothing to copy.
	 */
	private fun copySiteLink() {
		downloadDataModel?.siteReferrer
			?.takeIf { isValidURL(it) }
			?.let { fileUrl ->
				copyTextToClipboard(safeMotherActivityRef, fileUrl)
				showToast(
					activityInf = safeMotherActivityRef,
					msgId = R.string.title_file_url_has_been_copied
				)
				close()
			} ?: run {
			showToast(
				activityInf = safeMotherActivityRef,
				msgId = R.string.title_dont_have_anything_to_copy
			)
		}
	}
}