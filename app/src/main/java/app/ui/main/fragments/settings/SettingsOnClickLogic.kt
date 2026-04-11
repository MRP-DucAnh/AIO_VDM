package app.ui.main.fragments.settings

import android.content.*
import android.os.Environment.*
import android.widget.*
import androidx.annotation.*
import androidx.core.content.ContextCompat.*
import androidx.core.net.*
import app.core.AIOApp.Companion.AIO_DEFAULT_DOWNLOAD_PATH
import app.core.AIOApp.Companion.INSTANCE
import app.core.engines.settings.AIOSettings.Companion.DARK_MODE_INDICATOR_FIE
import app.core.engines.settings.AIOSettingsRepo.getSettings
import app.core.engines.updater.*
import app.ui.main.fragments.settings.activities.browser.*
import app.ui.main.fragments.settings.activities.downloads.*
import app.ui.main.fragments.settings.dialogs.*
import app.ui.others.information.*
import com.aio.*
import kotlinx.coroutines.*
import lib.device.*
import lib.files.FileSystemUtility.hasFullFileSystemAccess
import lib.files.FileSystemUtility.openAllFilesAccessSettings
import lib.networks.URLUtility.*
import lib.process.*
import lib.process.IntentHelperUtils.openInstagramApp
import lib.process.OSProcessUtils.restartApp
import lib.texts.CommonTextUtils.getText
import lib.ui.*
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*
import java.lang.ref.*

/**
 * A logic handler class responsible for managing user interaction events within the Settings screen.
 *
 * This class serves as a bridge between the [SettingsFragment] UI and the underlying application
 * business logic, including database updates via [app.core.engines.settings.AIOSettingsRepo],
 * system theme transitions, and external intent handling. By encapsulating click logic here,
 * the Fragment remains lightweight and focused strictly on view lifecycle management.
 *
 * Design Principles:
 * - **Memory Safety**: Uses [WeakReference] to avoid Activity/Fragment leaks during long-running
 * asynchronous tasks.
 * - **Thread Safety**: Strictly enforces context switching between [withMainContext] for UI
 * mutations and [withIOContext] for persistent storage operations.
 * - **UX Integrity**: Implements state-locking (e.g., [isFileFolderPickerActive]) to prevent
 * redundant dialog inflation and ensures haptic feedback for error states.
 *
 * @param settingsFragment The host fragment instance used for context and view access.
 */
class SettingsOnClickLogic(settingsFragment: SettingsFragment) {

	/**
	 * Utility for specialized logging within the Settings logic context.
	 * Leverages [LogHelperUtils] to provide tagged, formatted logs for debugging
	 * and error tracking.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the parent [SettingsFragment].
	 * * This is used to prevent memory leaks by allowing the Fragment to be
	 * garbage collected even if this logic class outlives it. This is critical
	 * for asynchronous operations that might complete after the Fragment is destroyed.
	 */
	private val weakReferenceOfSettingFragment = WeakReference(settingsFragment)

	/**
	 * Provides safe access to the [SettingsFragment] by unwrapping the [WeakReference].
	 * * If the Fragment has been destroyed and collected, this returns null,
	 * preventing [NullPointerException] or operations on a detached UI.
	 */
	private val safeSettingsFragmentRef: SettingsFragment?
		get() = weakReferenceOfSettingFragment.get()

	/**
	 * A state-lock flag used to track the visibility of the file/folder picker.
	 * * This prevents "double-click" scenarios where multiple picker dialogs could
	 * be opened simultaneously, which can lead to window leaks or inconsistent
	 * state in the UI.
	 */
	private var isFileFolderPickerActive = false

	/**
	 * Displays the custom dialog for selecting preferred download locations.
	 *
	 * This method initializes the [DownloadLocationSelector] component, which provides
	 * a user interface for choosing between internal storage, SD cards, or specific
	 * directory paths. It ensures thread safety by executing the UI-bound operation
	 * on the Main context.
	 */
	suspend fun showDownloadLocationPicker() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.let { activity ->
				DownloadLocationSelector(baseActivity = activity).show()
			}
		}
	}

	/**
	 * Toggles the application's global Dark Mode preference.
	 *
	 * This method implements a hybrid logic involving both file-system indicators
	 * and database persistence. It manages the creation or deletion of a theme
	 * indicator file used for early-init theme detection, updates the primary
	 * settings database, and triggers a system-level theme change within the UI.
	 *
	 * Logic Flow:
	 * 1. **IO Layer**: Manages the [DARK_MODE_INDICATOR_FIE] file status.
	 * 2. **Persistence**: Updates the central settings database.
	 * 3. **UI Layer**: Synchronizes toggle icons and calls [ViewUtility.changesSystemTheme]
	 * to apply the new theme globally across all active activities.
	 */
	suspend fun togglesDarkModeUISettings() {
		withIOContext {
			runCatching {
				val tempFile = File(INSTANCE.filesDir, DARK_MODE_INDICATOR_FIE)
				if (tempFile.exists()) tempFile.delete() else tempFile.createNewFile()
				getSettings().updateInDB()
				withMainContext {
					updateSettingStateUI()
					safeSettingsFragmentRef?.safeMotherActivityRef?.apply {
						ViewUtility.changesSystemTheme(this)
					}
				}
			}
		}
	}

	/**
	 * Triggers the selection workflow for the application's default content region.
	 *
	 * Changing the content region often requires a full application re-initialization
	 * to reload region-specific algorithms and data scrapers. This method displays
	 * the [ContentRegionSelector] and, upon user application, forces a clean
	 * application restart to ensure state integrity.
	 */
	suspend fun changeDefaultContentRegion() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.apply {
				ContentRegionSelector(this).apply {
					getDialogBuilder().setCancelable(true)
					onApplyListener = {
						safeSettingsFragmentRef?.safeMotherActivityRef
							?.activityCoroutineScope?.launch {
								close()
								restartApp(shouldKillProcess = true)
							}
					}
				}.show()
			}
		}
	}

	/**
	 * Toggles the visibility of daily personalized content recommendations.
	 *
	 * Updates the user's preference for suggestion algorithms. When disabled,
	 * the application hides the recommendation section from the main dashboard,
	 * providing a more minimalist interface.
	 */
	suspend fun toggleDailyContentSuggestions() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.apply {
				runCatching {
					val contentSuggestion = getSettings().isDailyContentSuggestionEnabled
					getSettings().isDailyContentSuggestionEnabled = !contentSuggestion
					getSettings().updateInDB()
					updateSettingStateUI()
				}
			}
		}
	}

	/**
	 * Orchestrates the workflow for changing the application's primary download directory.
	 *
	 * This complex interaction handles permission validation, state-locking to prevent
	 * duplicate dialogs, and the directory selection process itself.
	 *
	 * Logic Flow:
	 * 1. **Permission Check**: Verifies [hasFullFileSystemAccess]. If missing, it
	 * prompts the user with a mandatory dialog to redirect them to system settings.
	 * 2. **State Locking**: Uses [isFileFolderPickerActive] to ensure only one picker
	 * instance is active at a time.
	 * 3. **Picker Initialization**: Launches [FileFolderPicker] scoped to the external
	 * storage root.
	 * 4. **Result Handling**:
	 * - If valid: Updates [app.core.engines.settings.AIOSettings.selectedDownloadDirectory]
	 * and persists to DB.
	 * - If invalid/empty: Reverts to [AIO_DEFAULT_DOWNLOAD_PATH], provides haptic
	 * feedback, and notifies the user of the error.
	 * 5. **Safety**: Uses [runCatching] and explicit callbacks to ensure the
	 * active state flag is reset even if the process is aborted or fails.
	 */
	suspend fun changeDefaultDownloadFolder() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.let { activityRef ->
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
							activityRef.activityCoroutineScope.launch {
								close()
								openAllFilesAccessSettings(activityRef)
							}
						}
					}?.show()
				} else {
					if (isFileFolderPickerActive) return@withMainContext
					runCatching {
						FileFolderPicker(
							activityRef,
							isCancellable = true,
							initialPath = getExternalStorageDirectory().absolutePath,
							isFolderPickerOnly = true,
							isFilePickerOnly = false,
							isMultiSelection = false,
							titleText = getText(R.string.title_select_download_folder),
							positiveButtonText = getText(R.string.title_select_folder),
							onUserAbortedProcess = { isFileFolderPickerActive = false },
							onFileSelection = { listOfFolders ->
								activityRef.activityCoroutineScope.launch {
									if (listOfFolders[0].isEmpty()) {
										getSettings().selectedDownloadDirectory = AIO_DEFAULT_DOWNLOAD_PATH
										getSettings().updateInDB()
										isFileFolderPickerActive = false
										activityRef.doSomeVibration()
										showToast(activityRef, R.string.title_something_went_wrong)
										return@launch
									} else {
										getSettings().selectedDownloadDirectory = listOfFolders[0]
										getSettings().updateInDB()
										isFileFolderPickerActive = false
										showToast(activityRef, R.string.title_setting_applied)
										return@launch
									}
								}
							}).show()
					}.onFailure {
						isFileFolderPickerActive = false
					}
					isFileFolderPickerActive = true
				}
			}
		}
	}

	/**
	 * Toggles the visibility of active download notifications in the system tray.
	 *
	 * When enabled, the application suppresses ongoing download progress notifications,
	 * which can be useful for users who prefer a cleaner status bar or are performing
	 * a high volume of simultaneous downloads.
	 */
	suspend fun toggleHideDownloadNotification() {
		withIOContext {
			runCatching {
				val hideNotification = getSettings().downloadHideNotification
				getSettings().downloadHideNotification = !hideNotification
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Toggles the network constraint for download tasks.
	 *
	 * When active, this setting restricts all download activity to Wi-Fi connections only,
	 * automatically pausing or preventing tasks when the device is using cellular data
	 * to protect the user's data plan.
	 */
	suspend fun toggleWifiOnlyDownload() {
		withIOContext {
			runCatching {
				getSettings().downloadWifiOnly = !getSettings().downloadWifiOnly
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Toggles the interaction behavior for items in the finished tasks list.
	 *
	 * When enabled, a single tap on a completed download will immediately open the file
	 * using the system's default handler. This method also triggers a partial UI refresh
	 * in the finishedTasksListAdapter to ensure the click listeners are updated.
	 */
	suspend fun toggleSingleClickToOpenFile() {
		withIOContext {
			runCatching {
				val singleClickOpen = getSettings().openDownloadedFileOnSingleClick
				getSettings().openDownloadedFileOnSingleClick = !singleClickOpen
				getSettings().updateInDB()

				withMainContext {
					safeSettingsFragmentRef?.safeMotherActivityRef
						?.downloadFragment?.finishedTasksFragment
						?.finishedTasksListAdapter?.notifyDataSetChangedOnSort(true)
					updateSettingStateUI()
				}
			}
		}
	}

	/**
	 * Toggles the audible feedback for completed download notifications.
	 *
	 * This setting controls whether the application plays the system default
	 * notification sound when a download task reaches 100% completion.
	 */
	suspend fun toggleDownloadNotificationSound() {
		withIOContext {
			runCatching {
				getSettings().downloadPlayNotificationSound =
					!getSettings().downloadPlayNotificationSound
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Launches the Advanced Download Settings activity.
	 *
	 * This provides a navigation entry point for secondary download configurations
	 * (such as thread counts, buffer sizes, or concurrent task limits) that are
	 * grouped separately from the main settings list for better UX organization.
	 */
	suspend fun openAdvanceDownloadsSettings() {
		withMainContext {
			this@SettingsOnClickLogic.safeSettingsFragmentRef
				?.safeMotherActivityRef?.openActivity(
					targetActivity = AdvDownloadSettingsActivity::class.java,
					shouldAnimate = true
				)
		}
	}

	/**
	 * Orchestrates the interactive dialog for modifying the browser's default homepage.
	 *
	 * This method manages a complex UI interaction including dialog inflation, data
	 * validation, and soft keyboard management. It ensures that user-entered URLs
	 * are properly formatted and normalized (e.g., ensuring HTTPS) before persisting
	 * them to the database.
	 *
	 * Logic Flow:
	 * 1. Inflates the custom homepage selection layout.
	 * 2. Displays the current homepage as a reference to the user.
	 * 3. Validates input using [isValidURL] upon user confirmation.
	 * 4. Normalizes the URL via [ensureHttps] and updates the persistence layer.
	 * 5. Handles post-show UX by requesting focus and forcing the soft keyboard
	 * after a slight delay to ensure the window is stable.
	 *
	 * @see lib.networks.URLUtility.isValidURL
	 * @see lib.networks.URLUtility.ensureHttps
	 */
	suspend fun setBrowserDefaultHomepage() {
		withMainContext {
			runCatching {
				safeSettingsFragmentRef?.safeMotherActivityRef?.let { activityRef ->
					val dialogBuilder = DialogBuilder(activityRef)
					dialogBuilder.setView(R.layout.dialog_browser_homepage_1)

					val dialogLayout = dialogBuilder.view
					val stringResId = R.string.title_current_homepage
					val formatArgs = getSettings().browserDefaultHomepage
					val homepageString = activityRef.getString(stringResId, formatArgs)

					dialogLayout.findViewById<TextView>(R.id.txt_current_homepage).text = homepageString
					val editTextURL = dialogLayout.findViewById<EditText>(R.id.edit_field_url)

					dialogBuilder.setOnClickForPositiveButton {
						activityRef.activityCoroutineScope.launch {
							val userEnteredURL = editTextURL.text.toString()
							if (isValidURL(userEnteredURL)) {
								val finalNormalizedURL = ensureHttps(userEnteredURL) ?: userEnteredURL
								getSettings().browserDefaultHomepage = finalNormalizedURL
								getSettings().updateInDB()
								dialogBuilder.close()
								showToast(activityRef, R.string.title_successful)
							} else {
								activityRef.doSomeVibration()
								showToast(activityRef, R.string.title_invalid_url)
							}
						}
					}
					dialogBuilder.show()

					// Delay ensures the dialog's window is fully attached before keyboard request
					delay(500)
					editTextURL.post {
						editTextURL.requestFocus()
						showOnScreenKeyboard(activityRef, editTextURL)
					}
				}
			}
		}.onFailure { error ->
			logger.e("Error setting browser homepage: ${error.message}", error)
			showToast(
				activityInf = safeSettingsFragmentRef?.safeMotherActivityRef,
				msgId = R.string.title_something_went_wrong
			)
		}
	}

	/**
	 * Toggles the browser's content-level ad-blocking engine.
	 *
	 * This method updates the persistence layer to enable or disable the filtering of
	 * intrusive advertisements during web navigation. Once the database is updated,
	 * it triggers a UI refresh to reflect the new state.
	 */
	suspend fun toggleBrowserBrowserAdBlocker() {
		withIOContext {
			runCatching {
				val browserEnableAdblocker = getSettings().browserEnableAdblocker
				getSettings().browserEnableAdblocker = !browserEnableAdblocker
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Toggles the browser's anti-popup protection.
	 *
	 * Updates the setting responsible for intercepting and blocking secondary window
	 * creation attempts (pop-ups) by websites, improving the browsing experience
	 * and security.
	 */
	suspend fun toggleBrowserPopupAdBlocker() {
		withIOContext {
			runCatching {
				val browserEnablePopupBlocker = getSettings().browserEnablePopupBlocker
				getSettings().browserEnablePopupBlocker = !browserEnablePopupBlocker
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Toggles the global visibility of images within the browser.
	 *
	 * Disabling image loading can significantly reduce data consumption and
	 * decrease page load times, particularly on limited network connections.
	 */
	suspend fun toggleBrowserWebImages() {
		withIOContext {
			runCatching {
				getSettings().browserEnableImages = !getSettings().browserEnableImages
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Toggles the automated video detection and "grabber" feature.
	 *
	 * When enabled, the browser actively scans for downloadable video streams on
	 * visited pages, allowing the application to offer download options to the user.
	 */
	suspend fun toggleBrowserVideoGrabber() {
		withIOContext {
			runCatching {
				val enableVideoGrabber = getSettings().browserEnableVideoGrabber
				getSettings().browserEnableVideoGrabber = !enableVideoGrabber
				getSettings().updateInDB()
				updateSettingStateUI()
			}
		}
	}

	/**
	 * Launches the Advanced Browser Settings activity.
	 *
	 * Provides access to granular browser configurations that are not part of
	 * the primary settings list, utilizing a standard slide animation for the transition.
	 */
	suspend fun openAdvanceBrowserSettings() {
		withMainContext {
			this@SettingsOnClickLogic.safeSettingsFragmentRef
				?.safeMotherActivityRef?.openActivity(
					targetActivity = AdvBrowserSettingsActivity::class.java,
					shouldAnimate = true
				)
		}
	}

	/**
	 * Initiates the system-level share sheet to recommend the application.
	 *
	 * Generates a localized referral message via [getApplicationShareText] and
	 * delegates the intent delivery to [ShareUtility].
	 */
	suspend fun shareApplicationWithFriends() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.let { activityRef ->
				ShareUtility.shareText(
					context = activityRef,
					title = getText(R.string.title_share_with_others),
					text = getApplicationShareText(activityRef)
				)
			}
		}
	}

	/**
	 * Opens the internal feedback and bug reporting activity.
	 *
	 * Navigates the user to a dedicated interface for submitting user reviews,
	 * feature requests, or technical issues directly to the development team.
	 */
	suspend fun openUserFeedbackActivity() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.openActivity(
				UserFeedbackActivity::class.java, shouldAnimate = false
			)
		}
	}

	/**
	 * Navigates the user to the system's "App Info" settings page for this application.
	 *
	 * This provides a standardized entry point for users to manage system-level app
	 * configurations, such as clearing cache, managing granular permissions, or
	 * checking storage usage, by delegating the intent logic to the base activity.
	 */
	suspend fun openApplicationInformation() {
		withMainContext {
			val safeBaseActivityRef = this@SettingsOnClickLogic
				.safeSettingsFragmentRef?.safeBaseActivityRef
			safeBaseActivityRef?.openAppInfoSetting()
		}
	}

	/**
	 * Launches the official Privacy Policy URL in an external web browser.
	 *
	 * This method retrieves the localized URL from resources and attempts to fire
	 * an [Intent.ACTION_VIEW]. If no application on the device is capable of
	 * handling the web intent (e.g., no browser installed), it catches the exception,
	 * logs the event, and notifies the user via a toast.
	 */
	suspend fun showPrivacyPolicyActivity() {
		withMainContext {
			runCatching {
				val safeBaseActivityRef = this@SettingsOnClickLogic
					.safeSettingsFragmentRef?.safeBaseActivityRef
				safeBaseActivityRef?.let { activityRef ->
					try {
						val urlResId = R.string.text_aio_official_privacy_policy_url
						val uri = getText(urlResId)
						activityRef.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
					} catch (error: Exception) {
						logger.e("Error opening privacy policy: ${error.message}", error)
						val toastMsgId = R.string.title_please_install_web_browser
						showToast(activityRef, toastMsgId)
					}
				}
			}
		}
	}

	/**
	 * Launches the official Terms and Conditions URL in an external web browser.
	 *
	 * Similar to [showPrivacyPolicyActivity], this method handles the external
	 * navigation for legal documentation. It includes a fallback mechanism to
	 * inform the user if a web browser is unavailable on the system.
	 */
	suspend fun showTermsConditionActivity() {
		withMainContext {
			val safeBaseActivityRef = this@SettingsOnClickLogic
				.safeSettingsFragmentRef?.safeBaseActivityRef
			safeBaseActivityRef?.let { activityRef ->
				try {
					val uri = getText(R.string.text_aio_official_terms_conditions_url)
					activityRef.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
				} catch (error: Exception) {
					val toastMsgId = R.string.title_please_install_web_browser
					showToast(activityRef, toastMsgId)
				}
			}
		}
	}

	/**
	 * Initiates an asynchronous check for application updates and manages the feedback UI.
	 *
	 * This method displays a non-cancelable [WaitingDialog] to provide visual feedback while
	 * communicating with the update server. It uses a synthetic delay to ensure the UI transition
	 * feels smooth to the user before navigating to the official site or showing a "latest version"
	 * notification.
	 *
	 * Logic Flow:
	 * 1. Displays a [WaitingDialog] on the Main thread.
	 * 2. Introduces a 1000ms delay to prevent UI flickering on fast networks.
	 * 3. Queries [AIOUpdater.isNewUpdateAvailable] for version discrepancies.
	 * 4. If an update exists: Dismisses dialog and launches the browser to the official site.
	 * 5. If up to date: Dismisses dialog, triggers haptic feedback, and shows a success toast.
	 * 6. Error Handling: Catches any network or parsing exceptions, providing haptic feedback
	 * and an error toast.
	 */
	suspend fun checkForNewApkVersion() {
		withMainContext {
			this@SettingsOnClickLogic.safeSettingsFragmentRef
				?.safeBaseActivityRef?.let { activityRef ->
					runCatching {
						val waitingDialog = WaitingDialog(
							baseActivityInf = activityRef,
							loadingMessage = getText(R.string.title_checking_for_new_update),
							isCancelable = false,
						)
						waitingDialog.show()
						delay(1000)

						if (AIOUpdater().isNewUpdateAvailable()) {
							waitingDialog.close()
							activityRef.openApplicationOfficialSite()
						} else {
							waitingDialog.close()
							activityRef.doSomeVibration()
							val msgResId = R.string.title_you_using_latest_version
							showToast(activityRef, msgResId)
						}
					}.onFailure {
						activityRef.doSomeVibration()
						val toastMsgId = R.string.title_something_went_wrong
						showToast(activityRef, toastMsgId)
					}
				}
		}
	}

	/**
	 * Synchronizes the visual state of all toggleable settings in the UI with their
	 * underlying data sources.
	 *
	 * This method performs a batch update of the settings layout. It checks both the local
	 * file system (for theme indicators) and the persistent settings database to determine
	 * the correct "enabled" or "disabled" state for each UI component.
	 *
	 * Implementation Details:
	 * - Uses [SettingViewConfig] to map resource IDs to their current boolean state.
	 * - Iterates through the collection to find views and update their trailing drawables
	 * via [updateEndDrawable].
	 * - Operates strictly on the Main thread to ensure thread-safe UI mutations.
	 *
	 * Note: This should be called whenever the fragment is resumed or after any setting
	 * transaction is finalized.
	 */
	suspend fun updateSettingStateUI() {
		withMainContext {
			val darkModeTempConfigFile = File(INSTANCE.filesDir, DARK_MODE_INDICATOR_FIE)
			safeSettingsFragmentRef?.safeFragmentLayoutRef?.let { layout ->
				listOf(
					SettingViewConfig(R.id.txt_dark_mode_ui, darkModeTempConfigFile.exists()),
					SettingViewConfig(R.id.txt_daily_suggestions, getSettings().isDailyContentSuggestionEnabled),
					SettingViewConfig(R.id.txt_play_notification_sound, getSettings().downloadPlayNotificationSound),
					SettingViewConfig(R.id.txt_wifi_only_downloads, getSettings().downloadWifiOnly),
					SettingViewConfig(R.id.txt_single_click_open, getSettings().openDownloadedFileOnSingleClick),
					SettingViewConfig(R.id.txt_hide_task_notifications, getSettings().downloadHideNotification),
					SettingViewConfig(R.id.txt_enable_adblock, getSettings().browserEnableAdblocker),
					SettingViewConfig(R.id.txt_enable_popup_blocker, getSettings().browserEnablePopupBlocker),
					SettingViewConfig(R.id.txt_show_image_on_web, getSettings().browserEnableImages),
					SettingViewConfig(R.id.txt_enable_video_grabber, getSettings().browserEnableVideoGrabber),
				).forEach { config ->
					layout.findViewById<TextView>(config.viewId)?.updateEndDrawable(config.isEnabled)
				}
			}
		}
	}

	/**
	 * Orchestrates the user-facing workflow for restarting the application.
	 *
	 * This method presents a cautionary confirmation dialog to the user, explaining the
	 * implications of a restart (e.g., clearing the activity backstack or reinitializing
	 * system-level configurations).
	 *
	 * Logic Flow:
	 * 1. Validates the availability of the host Activity via a safe reference.
	 * 2. Inflates a localized confirmation dialog with an exit-themed positive button.
	 * 3. Upon user confirmation, triggers [restartApplicationProcess] within the
	 * [app.core.bases.BaseActivity.activityCoroutineScope] to ensure the process termination
	 * is handled gracefully relative to the Activity's lifecycle.
	 *
	 * @see restartApplicationProcess For the technical implementation of the process restart.
	 */
	suspend fun restartApplication() {
		withMainContext {
			this@SettingsOnClickLogic.safeSettingsFragmentRef
				?.safeBaseActivityRef?.let { activityRef ->
					val msgResId = R.string.text_cation_msg_of_restarting_application
					getMessageDialog(
						baseActivityInf = activityRef,
						isTitleVisible = true,
						titleText = getText(R.string.title_are_you_sure_about_this),
						messageTextViewCustomize = { it.setText(msgResId) },
						isNegativeButtonVisible = false,
						positiveButtonTextCustomize = {
							it.setLeftSideDrawable(R.drawable.ic_button_exit)
							it.setText(R.string.title_restart_application)
						}
					)?.apply {
						setOnClickForPositiveButton {
							activityRef.activityCoroutineScope.launch {
								restartApplicationProcess()
							}
						}
					}?.show()
				}
		}
	}

	/**
	 * Redirects the user to the developer's official Instagram profile.
	 *
	 * This method utilizes [openInstagramApp] to handle the redirection. It attempts
	 * to open the profile directly in the Instagram native application for the best
	 * user experience, falling back to a web browser if the app is not installed.
	 *
	 * @see lib.process.IntentHelperUtils.openInstagramApp
	 */
	suspend fun followDeveloperAtInstagram() {
		withMainContext {
			safeSettingsFragmentRef?.safeMotherActivityRef?.let {
				openInstagramApp(it, "https://www.instagram.com/shibafoss/")
			}
		}
	}

	/**
	 * Updates the end drawable (trailing icon) of a TextView to visually represent a toggle state.
	 *
	 * This utility method provides consistent visual feedback for toggleable settings by
	 * dynamically switching between checked (enabled) and unchecked (disabled) icons at
	 * the end of the text view. The approach maintains all existing drawables (start, top, bottom)
	 * while only modifying the end drawable to preserve the complete view composition.
	 *
	 * Visual Design:
	 * - Checked State: Filled circle icon indicating active/enabled setting
	 * - Unchecked State: Hollow circle icon indicating inactive/disabled setting
	 * - Position: Consistent end-aligned placement for predictable user scanning
	 * - Preservation: Maintains existing layout and other drawable positions
	 *
	 * Usage Pattern:
	 * Typically used in settings lists where each item has a toggle state that needs
	 * immediate visual feedback without requiring complete view recreation.
	 */
	private suspend fun TextView.updateEndDrawable(isEnabled: Boolean) {
		withMainContext {
			// Select appropriate drawable resource based on toggle state
			val endDrawableRes = if (isEnabled) R.drawable.ic_button_checked_circle_small
			else R.drawable.ic_button_unchecked_circle_small

			// Preserve existing drawables to maintain view composition
			val current = compoundDrawables
			val checkedDrawable = getDrawable(context, endDrawableRes)

			// Update only the end drawable while preserving others
			setCompoundDrawablesWithIntrinsicBounds(
				current[0], current[1], checkedDrawable, current[3]
			)
		}
	}

	/**
	 * Generates a localized and formatted share message containing application information
	 * and official page URL for social sharing and user referrals.
	 *
	 * This method constructs a user-friendly share message that includes the application name
	 * and official distribution page (Play Store, GitHub, or official website). The message
	 * is properly localized and formatted for clear communication across different languages
	 * and cultural contexts.
	 *
	 * Message Structure:
	 * - Application name with proper branding
	 * - Official distribution or information page URL
	 * - Localized invitation text appropriate for sharing contexts
	 * - Clean formatting with proper indentation handling
	 *
	 * Sharing Use Cases:
	 * - Social media platform sharing (Twitter, Facebook, WhatsApp)
	 * - Messaging app referrals to friends and contacts
	 * - Email recommendations with clickable links
	 * - Cross-promotion in related application communities
	 */
	private suspend fun getApplicationShareText(context: Context): String {
		return withIOContext {
			val appName = context.getString(R.string.title_aio_video_downloader)
			val githubOfficialPage = context.getString(R.string.text_aio_official_page_url)
			context.getString(R.string.text_sharing_app_msg, appName, githubOfficialPage)
				.trimIndent()
		}
	}

	/**
	 * Performs a complete application restart by launching the main activity and terminating
	 * the current process to ensure clean state reinitialization.
	 *
	 * This method implements a robust application restart mechanism that clears all existing
	 * activities from the back stack and creates a fresh application instance. The process
	 * termination guarantees that all static variables, cached data, and background services
	 * are completely reset, providing a clean slate equivalent to a fresh app launch.
	 *
	 * Restart Scenarios:
	 * - Language or locale changes requiring complete resource reload
	 * - Theme changes that need full activity recreation
	 * - Critical configuration updates requiring clean state
	 * - Recovery from unstable application states
	 *
	 * Technical Implementation:
	 * - CLEAR_TOP flag removes all activities from the back stack
	 * - NEW_TASK flag ensures proper task management
	 * - Process termination guarantees complete memory cleanup
	 * - Immediate activity launch provides seamless user experience
	 */
	private suspend fun restartApplicationProcess() {
		withMainContext {
			restartApp(shouldKillProcess = true)
		}
	}

	/**
	 * Data class representing the configuration state of a setting view for batch UI updates.
	 *
	 * This immutable data structure encapsulates the essential information needed to update
	 * multiple setting views efficiently. It enables batch processing of UI state changes
	 * by pairing view identifiers with their corresponding enabled/disabled states, which
	 * is particularly useful during settings initialization or bulk preference updates.
	 *
	 * Design Benefits:
	 * - Type-safe view identification using resource IDs
	 * - Immutable data structure for predictable state management
	 * - Efficient batch processing capabilities
	 * - Clear separation of configuration data from view logic
	 *
	 * Typical Usage:
	 * - Initial settings screen population from stored preferences
	 * - Bulk updates after configuration imports or resets
	 * - Theme or accessibility changes affecting multiple settings
	 * - Synchronization with remote configuration changes
	 */
	data class SettingViewConfig(
		/**
		 * Resource ID of the view to be updated, providing compile-time safety
		 * and enabling efficient view lookup through Android's resource system.
		 */
		@field:IdRes
		val viewId: Int,

		/**
		 * Boolean state indicating whether the setting is enabled (true) or disabled (false).
		 * This drives both visual representation and interactive behavior of the setting.
		 */
		val isEnabled: Boolean
	)
}