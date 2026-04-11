package app.ui.main.fragments.settings.activities.downloads

import android.os.Environment.*
import android.view.*
import android.widget.*
import app.core.AIOApp.Companion.AIO_DEFAULT_DOWNLOAD_PATH
import app.core.bases.*
import app.core.engines.settings.AIOSettingsRepo.getSettings
import app.ui.main.fragments.settings.activities.downloads.dialogs.*
import com.aio.*
import kotlinx.coroutines.*
import lib.files.FileSystemUtility.hasFullFileSystemAccess
import lib.files.FileSystemUtility.openAllFilesAccessSettings
import lib.process.*
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.setRightSideDrawable
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.*

/**
 * Activity responsible for managing advanced downloader configurations within the AIO ecosystem.
 *
 * This class provides a centralized interface for users to fine-tune download behaviors,
 * including storage locations, network constraints, UI notifications, and threading optimizations.
 * * Key Architectural Features:
 * * **Coroutines-First**: Leverages [activityCoroutineScope] with [withIOContext] for database
 * persistence and [withMainContext] for thread-safe UI updates.
 * * **Memory Efficiency**: Utilizes [WeakReference] patterns to prevent context leaks during
 * asynchronous operations.
 * * **Responsive UI**: Implements real-time toggle synchronization and dynamic dialog management
 * to ensure a smooth, mobile-optimized experience.
 */
class AdvDownloadSettingsActivity : BaseActivity() {

	/**
	 * Utility for logging diagnostic information and error tracking specifically
	 * for the [AdvDownloadSettingsActivity] context.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the current [AdvDownloadSettingsActivity] instance.
	 *
	 * This design pattern prevents memory leaks by allowing the Garbage Collector
	 * to reclaim the Activity during configuration changes (like screen rotation)
	 * even if an asynchronous process still holds a reference to this field.
	 */
	private val weakSelfReference = WeakReference(this)

	/**
	 * Provides null-safe access to the underlying Activity.
	 * * This computed property returns the Activity instance if it is still alive
	 * and attached to the window, or null if it has been destroyed. It is
	 * essential for preventing [IllegalStateException] or [NullPointerException]
	 * during [activityCoroutineScope] operations.
	 */
	private val safeActivityRef get() = weakSelfReference.get()

	/**
	 * Reference to the TextView that displays and toggles the status of download
	 * progress notifications in the system tray.
	 */
	private lateinit var txtHideNotification: TextView

	/**
	 * Reference to the TextView that displays and toggles the generation of
	 * video thumbnails for active and completed download tasks.
	 */
	private lateinit var txtHideTaskThumbnail: TextView

	/**
	 * Reference to the TextView that displays and toggles the network restriction
	 * allowing downloads to proceed only when connected to a Wi-Fi network.
	 */
	private lateinit var txtWifiOnlyDownload: TextView

	/**
	 * Reference to the TextView that displays and toggles the behavior of
	 * automatically opening a file when its corresponding task is clicked once.
	 */
	private lateinit var txtSingleClickOpen: TextView

	/**
	 * Reference to the TextView that displays and toggles the audible alert
	 * played upon the successful completion of a download.
	 */
	private lateinit var txtPlayDownloadSound: TextView

	/**
	 * Reference to the TextView that displays and toggles the "Smart Catalog"
	 * feature, which auto-organizes files into category-specific subfolders.
	 */
	private lateinit var txtSmartFolderCatalog: TextView

	/**
	 * Reference to the TextView that displays and toggles the intelligent
	 * multi-threading system for optimizing download connection speeds.
	 */
	private lateinit var txtSmartMultiThreading: TextView

	/**
	 * A state flag used to prevent the initialization of multiple concurrent
	 * [FileFolderPicker] instances, ensuring a single-task UI flow.
	 */
	private var isFileFolderPickerActive = false

	/**
	 * Specifies the XML layout resource to be inflated for this Activity.
	 *
	 * @return The resource ID [R.layout.activity_adv_download_settings_1], which
	 * defines the Advanced Download Settings interface.
	 */
	override fun onRenderingLayout(): Int {
		return R.layout.activity_adv_download_settings_1
	}

	/**
	 * Post-inflation lifecycle hook used to initialize the UI and its interactions.
	 *
	 * This method launches a coroutine within the [activityCoroutineScope] to ensure
	 * that all setup procedures—including view binding, click listener assignment,
	 * and initial state synchronization—are handled asynchronously and safely.
	 * It uses [safeActivityRef] to avoid interacting with a destroyed Activity context.
	 */
	override fun onAfterLayoutRender() {
		activityCoroutineScope.launch {
			safeActivityRef?.let { activityRef ->
				initializeViews(activityRef)
				initializeViewClickListeners(activityRef)
				updateAllToggleStates()
			}
		}
	}

	/**
	 * Handles the hardware back button or software back navigation event.
	 *
	 * Overrides the default behavior to execute [closeActivityWithFadeAnimation],
	 * providing a smooth visual transition back to the previous screen.
	 */
	override fun onBackPressActivity() {
		closeActivityWithFadeAnimation(true)
	}

	/**
	 * Binds layout XML components to their respective [TextView] properties.
	 *
	 * This method ensures all toggle-capable text views are correctly referenced
	 * from the inflated layout. It is executed within [withMainContext] to guarantee
	 * safe interaction with the [View] hierarchy.
	 *
	 * @param activityRef A stable reference to the [AdvDownloadSettingsActivity]
	 * context used for resource lookup.
	 */
	private suspend fun initializeViews(activityRef: AdvDownloadSettingsActivity) {
		withMainContext {
			with(activityRef) {
				txtHideNotification = findViewById(R.id.txt_hide_notifications)
				txtHideTaskThumbnail = findViewById(R.id.txt_hide_thumbnails)
				txtWifiOnlyDownload = findViewById(R.id.txt_wifi_only)
				txtSingleClickOpen = findViewById(R.id.txt_single_click)
				txtPlayDownloadSound = findViewById(R.id.txt_notification_sound)
				txtSmartFolderCatalog = findViewById(R.id.txt_smart_catalog)
				txtSmartMultiThreading = findViewById(R.id.txt_auto_thread)
			}
		}
	}

	/**
	 * Establishes a functional mapping between UI interaction points and their
	 * respective business logic.
	 *
	 * This method utilizes a declarative approach by mapping resource IDs to
	 * [suspend] lambda actions. It sets [View.OnClickListener]s that bridge
	 * synchronous UI events into the [activityCoroutineScope], ensuring that
	 * heavy operations (like database writes or dialog inflation) are managed
	 * appropriately without blocking the Main thread.
	 *
	 * @param activityRef The [AdvDownloadSettingsActivity] context used to find
	 * views and provide scope for coroutine launches.
	 */
	private suspend fun initializeViewClickListeners(activityRef: AdvDownloadSettingsActivity) {
		withMainContext {
			val clickActions: Map<Int, suspend () -> Unit> = mapOf(
				R.id.btn_left_actionbar to { onBackPressActivity() },
				R.id.btn_download_dir to { showDownloadFolderPicker() },
				R.id.btn_hide_notifications to { toggleHideNotification() },
				R.id.btn_hide_thumbnails to { toggleHideThumbnails() },
				R.id.btn_wifi_only to { toggleWifiOnlyDownloads() },
				R.id.btn_single_click to { toggleSingleClickOpen() },
				R.id.btn_notification_sound to { togglePlayDownloadSound() },
				R.id.btn_auto_resume to { showMaxAutoResumeRetryDialog() },
				R.id.btn_smart_catalog to { toggleSmartFolderCatalog() },
				R.id.btn_auto_thread to { toggleSmartMultiThreading() },
				R.id.btn_max_parallel to { showMaxParallelDownloadDialog() },
				R.id.btn_user_agent to { showDownloadUserAgentSelector() },
				R.id.btn_speed_limit to { showMaxDownloadSpeedLimitDialog() },
				R.id.btn_reset to { showResetDownloadSettingDialog() }
			)

			clickActions.forEach { (id, action) ->
				activityRef.findViewById<View>(id)?.setOnClickListener {
					activityCoroutineScope.launch { action() }
				}
			}
		}
	}

	/**
	 * Refreshes the visual state of all toggle components based on current settings.
	 *
	 * This method performs a batch update of the UI layer to ensure that the icons
	 * reflect the most recent data stored in [getSettings]. It synchronizes various
	 * download configurations including notification visibility, thumbnail preferences,
	 * and automated logic toggles.
	 *
	 * The operation is execution-safe within [withMainContext] to prevent
	 * CalledFromWrongThreadExceptions during bulk UI updates.
	 */
	private suspend fun updateAllToggleStates() {
		withMainContext {
			updateToggleIcon(txtHideNotification, getSettings().downloadHideNotification)
			updateToggleIcon(txtHideTaskThumbnail, getSettings().downloadHideVideoThumbnail)
			updateToggleIcon(txtWifiOnlyDownload, getSettings().downloadWifiOnly)
			updateToggleIcon(txtSingleClickOpen, getSettings().openDownloadedFileOnSingleClick)
			updateToggleIcon(txtPlayDownloadSound, getSettings().downloadPlayNotificationSound)
			updateToggleIcon(txtSmartFolderCatalog, getSettings().downloadAutoFolderCatalog)
			updateToggleIcon(txtSmartMultiThreading, getSettings().downloadAutoThreadSelection)
		}
	}

	/**
	 * Updates the compound drawable of a [TextView] to represent a boolean state.
	 *
	 * @param textView The target view to update.
	 * @param isEnabled If true, displays the checked circle icon; otherwise,
	 * displays the unchecked circle icon.
	 *
	 * This is a thread-safe UI operation wrapped in [withMainContext] that utilizes
	 * [setRightSideDrawable] to maintain consistent alignment within the settings list.
	 */
	private suspend fun updateToggleIcon(textView: TextView, isEnabled: Boolean) {
		withMainContext {
			val iconRes = if (isEnabled) R.drawable.ic_button_checked_circle_small
			else R.drawable.ic_button_unchecked_circle_small
			textView.setRightSideDrawable(iconRes, true)
		}
	}

	/**
	 * Initiates the directory selection workflow for defining a custom download location.
	 *
	 * This method follows a two-tier verification logic:
	 * 1. **Permission Check**: Verifies if the application has Scoped Storage / Full File System
	 * Access. If missing, it presents a [getMessageDialog] directing the user to system settings.
	 * 2. **Picker Execution**: If permissions are granted, it launches the [FileFolderPicker].
	 * * State Management:
	 * * Prevents multiple picker instances via the [isFileFolderPickerActive] flag.
	 * * Offloads database persistence to [withIOContext] once a directory is selected.
	 * * Falls back to [AIO_DEFAULT_DOWNLOAD_PATH] if the selection returns an invalid or empty path.
	 *
	 * All UI components and dialog triggers are strictly confined to [withMainContext].
	 */
	private suspend fun showDownloadFolderPicker() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				if (!hasFullFileSystemAccess(activityRef)) {
					getMessageDialog(
						baseActivityInf = activityRef,
						isTitleVisible = true,
						isCancelable = false,
						isNegativeButtonVisible = false,
						titleText = activityRef.getString(R.string.title_storage_permission_needed),
						messageTextViewCustomize = { it.setText(R.string.text_file_system_permission_needed) },
						positiveButtonTextCustomize = { it.setText(R.string.title_allow_now_in_settings) }
					)?.apply {
						setOnClickForPositiveButton {
							activityCoroutineScope.launch {
								close()
								openAllFilesAccessSettings(activityRef)
							}
						}
					}?.show()
				} else {
					if (isFileFolderPickerActive) return@withMainContext

					FileFolderPicker(
						activityRef,
						isCancellable = true,
						initialPath = getExternalStorageDirectory().absolutePath,
						isFolderPickerOnly = true,
						onUserAbortedProcess = { isFileFolderPickerActive = false },
						onFileSelection = { listOfFolders ->
							activityCoroutineScope.launch {
								withIOContext {
									if (listOfFolders[0].isEmpty()) {
										getSettings().selectedDownloadDirectory = AIO_DEFAULT_DOWNLOAD_PATH
										getSettings().updateInDB()
										withMainContext {
											isFileFolderPickerActive = false
											activityRef.doSomeVibration()
											showToast(activityRef, R.string.title_something_went_wrong)
										}
									} else {
										getSettings().selectedDownloadDirectory = listOfFolders[0]
										getSettings().updateInDB()
										withMainContext {
											isFileFolderPickerActive = false
											showToast(activityRef, R.string.title_setting_applied)
										}
									}
								}
							}
						}).show()

					isFileFolderPickerActive = true
				}
			}
		}
	}

	/**
	 * Toggles the visibility of download progress notifications in the system tray.
	 *
	 * This method modifies the user's notification preference within [withIOContext] to
	 * ensure the database write is offloaded from the Main thread. Once the state
	 * is persisted, the UI icon is synchronized to reflect the new setting.
	 */
	private suspend fun toggleHideNotification() {
		withIOContext {
			getSettings().downloadHideNotification =
				!getSettings().downloadHideNotification

			getSettings().updateInDB()
			updateToggleIcon(
				txtHideNotification,
				getSettings().downloadHideNotification
			)

		}
	}

	/**
	 * Toggles the generation and display of video thumbnails within the download manager.
	 *
	 * Disabling this can reduce disk I/O and CPU usage on lower-end devices. The setting
	 * is updated asynchronously in [withIOContext], maintaining a smooth interface
	 * while the background persistence logic executes.
	 */
	private suspend fun toggleHideThumbnails() {
		withIOContext {
			getSettings().downloadHideVideoThumbnail =
				!getSettings().downloadHideVideoThumbnail

			getSettings().updateInDB()
			updateToggleIcon(
				txtHideTaskThumbnail,
				getSettings().downloadHideVideoThumbnail
			)

		}
	}

	/**
	 * Toggles the network constraint for download tasks to permit transfers only over Wi-Fi.
	 *
	 * When enabled, active downloads will pause if the device switches to mobile data.
	 * The state transition is handled in [withIOContext] to ensure thread-safe
	 * repository updates without impacting the UI responsiveness.
	 */
	private suspend fun toggleWifiOnlyDownloads() {
		withIOContext {
			getSettings().downloadWifiOnly = !getSettings().downloadWifiOnly
			getSettings().updateInDB()
			updateToggleIcon(txtWifiOnlyDownload, getSettings().downloadWifiOnly)
		}
	}

	/**
	 * Toggles the interaction behavior for completed downloads in the list.
	 *
	 * If enabled, a single tap on a finished task will immediately trigger the
	 * system 'Open' intent. This configuration change is performed within
	 * [withIOContext] for consistent data handling.
	 */
	private suspend fun toggleSingleClickOpen() {
		withIOContext {
			getSettings().openDownloadedFileOnSingleClick =
				!getSettings().openDownloadedFileOnSingleClick

			getSettings().updateInDB()
			updateToggleIcon(
				txtSingleClickOpen,
				getSettings().openDownloadedFileOnSingleClick
			)

		}
	}

	/**
	 * Toggles whether the application plays a dedicated audio cue upon download completion.
	 *
	 * This method updates the audio preference in [withIOContext], ensuring that the
	 * settings database remains the single source of truth while keeping the
	 * application's main loop unblocked.
	 */
	private suspend fun togglePlayDownloadSound() {
		withIOContext {
			getSettings().downloadPlayNotificationSound =
				!getSettings().downloadPlayNotificationSound

			getSettings().updateInDB()
			updateToggleIcon(
				txtPlayDownloadSound,
				getSettings().downloadPlayNotificationSound
			)

		}
	}

	/**
	 * Displays a dialog to configure the maximum number of automated reconnection attempts.
	 *
	 * This method initializes the [GlobalMaxAutoRetryLimit] selector on the Main thread.
	 * It provides a success callback that uses [activityCoroutineScope] to safely
	 * dispatch a confirmation toast, ensuring that any UI feedback respects the
	 * activity's lifecycle.
	 */
	private suspend fun showMaxAutoResumeRetryDialog() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				try {
					GlobalMaxAutoRetryLimit(activityRef) {
						activityCoroutineScope.launch {
							showToast(activityRef, R.string.title_setting_applied)
						}
					}.showDialog()
				} catch (error: Exception) {
					logger.e("Error while showing max auto-resume retry dialog", error)
					activityRef.doSomeVibration()
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	/**
	 * Toggles the automated download organization system.
	 *
	 * When enabled, the application will automatically sort downloads into sub-folders
	 * based on file type (e.g., Video, Audio, Documents). This method executes the
	 * state change and database persistence within [withIOContext] to ensure zero
	 * impact on UI fluidity, followed by a thread-safe icon update.
	 */
	private suspend fun toggleSmartFolderCatalog() {
		withIOContext {
			getSettings().downloadAutoFolderCatalog = !getSettings().downloadAutoFolderCatalog
			getSettings().updateInDB()
			updateToggleIcon(txtSmartFolderCatalog, getSettings().downloadAutoFolderCatalog)
		}
	}

	/**
	 * Switches the intelligent multi-threading selection logic.
	 *
	 * This setting allows the engine to dynamically determine the optimal number of
	 * connection threads based on the server's capabilities and current network conditions.
	 * The update is performed in [withIOContext] to maintain repository consistency
	 * without blocking the Main thread.
	 */
	private suspend fun toggleSmartMultiThreading() {
		withIOContext {
			getSettings().downloadAutoThreadSelection = !getSettings().downloadAutoThreadSelection
			getSettings().updateInDB()
			updateToggleIcon(txtSmartMultiThreading, getSettings().downloadAutoThreadSelection)
		}
	}

	/**
	 * Displays a configuration dialog to set the maximum number of concurrent download tasks.
	 *
	 * This method initializes the [GlobalMaxParallelDownloads] dialog on the UI thread
	 * using [withMainContext]. It passes a callback that utilizes the [activityCoroutineScope]
	 * to provide non-blocking visual feedback (Toast) once the user applies the new
	 * concurrency limit.
	 */
	private suspend fun showMaxParallelDownloadDialog() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				try {
					GlobalMaxParallelDownloads(activityRef) {
						activityCoroutineScope.launch {
							showToast(activityRef, R.string.title_setting_applied)
						}
					}.showDialog()
				} catch (error: Exception) {
					logger.e("Error showing max parallel dialog", error)
					activityRef.doSomeVibration()
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	/**
	 * Orchestrates the display of the User Agent selection dialog for download requests.
	 *
	 * This method initializes the [GlobalDownloadUserAgent] component and defines an
	 * asynchronous callback for the selection event. When a new agent is applied:
	 * 1. It launches a coroutine to handle persistence in [withIOContext].
	 * 2. Upon database success, it switches back to [withMainContext] to provide
	 * visual confirmation and dismiss the selector.
	 *
	 * Failure to initialize the dialog triggers haptic feedback and an error notification.
	 */
	private suspend fun showDownloadUserAgentSelector() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				try {
					GlobalDownloadUserAgent(activityRef).apply {
						onApplyListener = { updatedSettings ->
							activityCoroutineScope.launch {
								withIOContext {
									updatedSettings.updateInDB()
									withMainContext {
										showToast(activityRef, R.string.title_setting_applied)
										close()
									}
								}
							}
						}
					}.show()
				} catch (error: Exception) {
					logger.e("Error showing download user agent selector", error)
					activityRef.doSomeVibration()
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	/**
	 * Invokes the speed limiting configuration dialog to throttle download bandwidth.
	 *
	 * This method manages the lifecycle of the [GlobalDownloadSpeedLimit] dialog.
	 * It ensures that once the user confirms a speed limit, the success notification
	 * is dispatched safely via the [activityCoroutineScope].
	 *
	 * Comprehensive error handling is included to catch potential window manager
	 * or inflation exceptions, ensuring activity stability.
	 */
	private suspend fun showMaxDownloadSpeedLimitDialog() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				try {
					GlobalDownloadSpeedLimit(activityRef) {
						activityCoroutineScope.launch {
							showToast(activityRef, R.string.title_setting_applied)
						}
					}.showDialog()
				} catch (error: Exception) {
					logger.e("Error showing download speed limiter", error)
					activityRef.doSomeVibration()
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}

	/**
	 * Displays a confirmation dialog to warn the user before reverting download configurations.
	 *
	 * This method uses [getMessageDialog] to present a non-cancelable warning. Upon
	 * confirmation, it bridges the synchronous dialog click event to the
	 * asynchronous [resetDownloadSettings] logic using the [activityCoroutineScope].
	 *
	 * The dialog configuration includes:
	 * * **Main-Safety**: Wrapped in [withMainContext] to ensure the MsgDialog
	 * is inflated and attached on the UI thread.
	 * * **UX Feedback**: Customizes the positive button with a clear action icon
	 * and localized reset text.
	 */
	private suspend fun showResetDownloadSettingDialog() {
		withMainContext {
			safeActivityRef?.let { activity ->
				getMessageDialog(
					baseActivityInf = activity,
					isTitleVisible = true,
					isNegativeButtonVisible = false,
					titleTextViewCustomize = { it.setText(R.string.title_reset_download_settings) },
					messageTextViewCustomize = { it.setText(R.string.text_reset_download_settings) },
					positiveButtonTextCustomize = {
						it.setText(R.string.title_reset_now)
						it.setLeftSideDrawable(R.drawable.ic_button_clear)
					}
				)?.apply {
					setOnClickForPositiveButton {
						activityCoroutineScope.launch {
							resetDownloadSettings()
							close()
						}
					}
					show()
				}
			}
		}
	}

	/**
	 * Restores all download-related configurations to their factory default values.
	 *
	 * This method orchestrates a multi-threaded reset process:
	 * 1. **Data Persistence**: Swaps to [withIOContext] to perform a batch update of the
	 * [getSettings] object. It resets paths, notification preferences, network
	 * constraints (Wifi-only, speed limits), and concurrency settings (parallel connections).
	 * 2. **UI Synchronization**: Switches back to [withMainContext] to refresh the
	 * visual toggle states across the activity and display a success toast to the user.
	 *
	 * This ensures that the disk-heavy database write does not block the UI thread,
	 * maintaining the app's "AIO" high-performance standard.
	 */
	private suspend fun resetDownloadSettings() {
		withIOContext {
			getSettings().apply {
				val resId = R.string.text_browser_default_mobile_http_user_agent
				selectedDownloadDirectory = AIO_DEFAULT_DOWNLOAD_PATH
				downloadHideNotification = false
				downloadHideVideoThumbnail = false
				downloadWifiOnly = false
				openDownloadedFileOnSingleClick = true
				downloadPlayNotificationSound = true
				downloadAutoResumeMaxErrors = 32
				downloadAutoFolderCatalog = true
				downloadAutoThreadSelection = true
				downloadDefaultParallelConnections = 1
				downloadHttpUserAgent = getString(resId)
				downloadMaxNetworkSpeed = 0
				updateInDB()
			}
			withMainContext {
				updateAllToggleStates()
				val msgId = R.string.title_downloads_settings_reset_to_defaults
				showToast(safeActivityRef, msgId)
			}
		}
	}
}