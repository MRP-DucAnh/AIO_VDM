package app.ui.main.fragments.settings.activities.browser

import android.view.*
import android.widget.*
import app.core.bases.*
import app.core.engines.browser.AIOWebRecordsRepo.getHistoryRecordsIds
import app.core.engines.browser.AIOWebRecordsRepo.getWebRecordsBox
import app.core.engines.settings.AIOSettingsRepo.getSettings
import app.ui.main.fragments.settings.activities.browser.dialogs.*
import com.aio.*
import kotlinx.coroutines.*
import lib.networks.URLUtility.*
import lib.process.*
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import lib.ui.*
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.setRightSideDrawable
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.*

/**
 * Activity responsible for managing advanced browser configurations and maintenance tasks.
 *
 * This activity provides a comprehensive interface for users to toggle core browser engine
 * features such as JavaScript execution, content filtering (Adblock), and private browsing.
 * It also facilitates administrative actions including cache purification and factory
 * reset of browser settings.
 *
 * Key Architectural Features:
 * * **Main-Safety**: Utilizes [withMainContext] and [withIOContext] to strictly segregate
 * UI updates from heavy disk and database I/O, ensuring a stutter-free experience.
 * * **Leak Prevention**: Employs [WeakReference] and [activityCoroutineScope] to ensure
 * background tasks do not reference the Activity after it has been destroyed.
 * * **Atomic Updates**: Ensures that changes to [app.core.engines.settings.AIOSettingsRepo]
 * are persisted to the ObjectBox database immediately following user interaction.
 *
 * @see BaseActivity The foundational activity class providing coroutine scopes and layout management.
 */
class AdvBrowserSettingsActivity : BaseActivity() {
	/**
	 * Utility for logging diagnostic messages and errors specific to the advanced
	 * browser settings logic, using the class name as the identifying tag.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the current Activity instance used to prevent memory leaks
	 * during long-running background coroutines or asynchronous callbacks.
	 */
	private val weakSelfReference = WeakReference(this)

	/**
	 * Provides a null-safe way to access the Activity context. If the Activity has
	 * been destroyed or reclaimed by the Garbage Collector, this returns null,
	 * effectively canceling pending UI operations.
	 */
	private val safeActivityRef get() = weakSelfReference.get()

	/**
	 * TextView component used to display and toggle the status of JavaScript execution.
	 */
	private lateinit var txtEnableJavascript: TextView

	/**
	 * TextView component used to display and toggle the status of the integrated adblocker.
	 */
	private lateinit var txtEnableAdblock: TextView

	/**
	 * TextView component used to display and toggle whether images are loaded on web pages.
	 */
	private lateinit var txtShowImageOnWeb: TextView

	/**
	 * TextView component used to display and toggle the background video detection utility.
	 */
	private lateinit var txtEnableVideoGrabber: TextView

	/**
	 * TextView component used to display and toggle the suppression of web pop-up windows.
	 */
	private lateinit var txtEnablePopupBlocker: TextView

	/**
	 * TextView component used to display and toggle the desktop-site rendering preference.
	 */
	private lateinit var txtEnableDesktopMode: TextView
	private lateinit var txtEnablePrivateMode: TextView

	/**
	 * Specifies the XML layout resource to be inflated for this activity.
	 *
	 * @return The resource ID of the browser settings layout.
	 */
	override fun onRenderingLayout(): Int {
		return R.layout.activity_adv_browser_settings_1
	}

	/**
	 * Lifecycle callback executed immediately after the layout has been rendered.
	 *
	 * Launches a coroutine in the [activityCoroutineScope] to safely orchestrate
	 * the initialization of views, binding of listeners, and the initial
	 * synchronization of setting toggle states.
	 */
	override fun onAfterLayoutRender() {
		activityCoroutineScope.launch {
			safeActivityRef?.apply {
				initializeViews()
				initializeViewClickListeners()
				updateAllToggleStates()
			}
		}
	}

	/**
	 * Handles the back button press event with a specialized exit animation.
	 *
	 * Overrides the default behavior to provide a smooth fade transition,
	 * improving the perceived performance and aesthetic of the browser UI.
	 */
	override fun onBackPressActivity() {
		closeActivityWithFadeAnimation(true)
	}

	/**
	 * Binds the XML view components to their respective class properties.
	 *
	 * This method uses [withMainContext] to ensure all [findViewById] calls
	 * occur on the Main thread. By caching these [TextView] references early,
	 * subsequent toggle updates can be performed with zero lookup overhead.
	 */
	private suspend fun initializeViews() {
		withMainContext {
			safeActivityRef?.apply {
				txtEnableJavascript = findViewById(R.id.txt_enable_javascript)
				txtEnableAdblock = findViewById(R.id.txt_enable_adblock)
				txtShowImageOnWeb = findViewById(R.id.txt_show_image_on_web)
				txtEnableVideoGrabber = findViewById(R.id.txt_enable_video_grabber)
				txtEnablePopupBlocker = findViewById(R.id.txt_enable_popup_blocker)
				txtEnableDesktopMode = findViewById(R.id.txt_enable_desktop_mode)
				txtEnablePrivateMode = findViewById(R.id.txt_enable_private_mode)
			}
		}
	}

	/**
	 * Initializes click listeners for the UI components using a centralized [View.OnClickListener].
	 *
	 * This method maps multiple view resource IDs to a single listener instance that branches
	 * execution based on the clicked View's ID. Each action is wrapped in a new coroutine
	 * launched within the [activityCoroutineScope] to ensure that long-running tasks or
	 * context switches do not block the UI thread's click-response cycle.
	 *
	 * @see withMainContext For ensuring thread-safe view binding.
	 */
	private suspend fun initializeViewClickListeners() {
		withMainContext {
			safeActivityRef?.apply {
				val clickListener = View.OnClickListener { view ->
					activityCoroutineScope.launch {
						when (view.id) {
							R.id.btn_left_actionbar -> onBackPressActivity()
							R.id.btn_browser_homepage -> showHomepageEditor()
							R.id.btn_enable_javascript -> toggleJavascript()
							R.id.btn_enable_adblock -> toggleAdblock()
							R.id.btn_show_image_on_web -> toggleShowImages()
							R.id.btn_enable_video_grabber -> toggleVideoGrabber()
							R.id.btn_enable_popup_blocker -> togglePopupBlocker()
							R.id.btn_enable_desktop_mode -> toggleDesktopMode()
							R.id.btn_enable_private_mode -> togglePrivateMode()
							R.id.btn_browser_useragent -> showUserAgentEditor()
							R.id.btn_clear_browser_caches -> showClearCacheDialog()
							R.id.btn_reset_browser_settings -> showResetSettingsDialog()
						}
					}
				}

				listOf(
					R.id.btn_left_actionbar,
					R.id.btn_browser_homepage,
					R.id.btn_enable_javascript,
					R.id.btn_enable_adblock,
					R.id.btn_show_image_on_web,
					R.id.btn_enable_video_grabber,
					R.id.btn_enable_popup_blocker,
					R.id.btn_enable_desktop_mode,
					R.id.btn_enable_private_mode,
					R.id.btn_browser_useragent,
					R.id.btn_clear_browser_caches,
					R.id.btn_reset_browser_settings
				).forEach { id ->
					findViewById<View>(id)?.setOnClickListener(clickListener)
				}
			}
		}
	}

	/**
	 * Refreshes the visual state of all setting toggles in the Activity.
	 *
	 * This method retrieves the latest browser configurations from the database and
	 * synchronizes the [TextView] drawables accordingly. It is typically called
	 * during initial layout rendering or after a batch reset of settings.
	 */
	private suspend fun updateAllToggleStates() {
		withMainContext {
			updateToggleIcon(txtEnableJavascript, getSettings().browserEnableJavascript)
			updateToggleIcon(txtEnableAdblock, getSettings().browserEnableAdblocker)
			updateToggleIcon(txtShowImageOnWeb, getSettings().browserEnableImages)
			updateToggleIcon(txtEnableVideoGrabber, getSettings().browserEnableVideoGrabber)
			updateToggleIcon(txtEnablePopupBlocker, getSettings().browserEnablePopupBlocker)
			updateToggleIcon(txtEnableDesktopMode, getSettings().browserDesktopBrowsing)
			updateToggleIcon(txtEnablePrivateMode, getSettings().browserPrivateBrowsing)
		}
	}

	/**
	 * Updates the compound drawable of a specific [TextView] based on a boolean state.
	 *
	 * Maps an enabled or disabled state to a specific 'circle' icon resource. This
	 * method is explicitly marked as [withMainContext] to prevent threading errors
	 * when modifying view properties from background scopes.
	 *
	 * @param textView  The text-based UI element to be updated.
	 * @param isEnabled The state indicating whether the checked or unchecked icon is used.
	 */
	private suspend fun updateToggleIcon(textView: TextView, isEnabled: Boolean) {
		withMainContext {
			val iconRes = if (isEnabled) R.drawable.ic_button_checked_circle_small
			else R.drawable.ic_button_unchecked_circle_small
			textView.setRightSideDrawable(iconRes, true)
		}
	}

	/**
	 * Launches a custom dialog to edit and validate the browser's default homepage.
	 *
	 * This method initializes a [DialogBuilder] with a specialized URL entry layout.
	 * It retrieves the current homepage from settings to display as a reference.
	 * Upon the user clicking the positive action:
	 * 1. **Validation**: Checks if the entered string is a syntactically valid URL.
	 * 2. **Normalization**: Automatically prepends 'https://' if missing via [ensureHttps].
	 * 3. **Persistence**: Updates the database within [withIOContext].
	 * 4. **UX**: Provides haptic feedback on failure or a success toast on completion.
	 *
	 * Additionally, it includes a 200ms delayed trigger to ensure the soft keyboard
	 * is properly displayed after the dialog animation completes.
	 */
	private suspend fun showHomepageEditor() {
		withMainContext {
			runCatching {
				safeActivityRef?.let { activityRef ->
					val dialogBuilder = DialogBuilder(activityRef)
					dialogBuilder.setView(R.layout.dialog_browser_homepage_1)

					val dialogLayout = dialogBuilder.view
					val stringResId = R.string.title_current_homepage
					val formatArgs = getSettings().browserDefaultHomepage
					val homepageString = activityRef.getString(stringResId, formatArgs)

					dialogLayout.findViewById<TextView>(R.id.txt_current_homepage).text = homepageString
					val editTextURL = dialogLayout.findViewById<EditText>(R.id.edit_field_url)

					dialogBuilder.setOnClickForPositiveButton {
						activityCoroutineScope.launch {
							withIOContext {
								val userEnteredURL = editTextURL.text.toString()
								if (isValidURL(userEnteredURL)) {
									val finalNormalizedURL = ensureHttps(userEnteredURL) ?: userEnteredURL
									getSettings().browserDefaultHomepage = finalNormalizedURL
									getSettings().updateInDB()
									withMainContext {
										dialogBuilder.close()
										showToast(activityRef, R.string.title_successful)
									}
								} else {
									withMainContext {
										activityRef.doSomeVibration()
										showToast(activityRef, R.string.title_invalid_url)
									}
								}
							}
						}
					}

					dialogBuilder.show()

					delay(200, object : OnTaskFinishListener {
						override fun afterDelay() {
							activityCoroutineScope.launch {
								editTextURL.requestFocus()
								showOnScreenKeyboard(activityRef, editTextURL)
							}
						}
					})
				}
			}.onFailure { error ->
				logger.e("Error setting browser homepage: ${error.message}", error)
				showToast(safeActivityRef, R.string.title_something_went_wrong)
			}
		}
	}

	/**
	 * Toggles the browser's JavaScript execution capability.
	 *
	 * Flips the current browserEnableJavascript state and persists the change to the
	 * local database. This triggers a UI update for the JavaScript toggle icon and
	 * logs the event for debugging.
	 */
	private suspend fun toggleJavascript() {
		withMainContext {
			getSettings().browserEnableJavascript = !getSettings().browserEnableJavascript
			getSettings().updateInDB()
			updateToggleIcon(txtEnableJavascript, getSettings().browserEnableJavascript)
			logger.d("Web JavaScript enabled: ${getSettings().browserEnableJavascript}")
		}
	}

	/**
	 * Toggles the integrated content filter (Adblocker).
	 *
	 * Inverts the browserEnableAdblocker setting, ensuring the preference is saved
	 * to the database. The UI is refreshed to reflect the new state, and the
	 * transition is recorded in the logs.
	 */
	private suspend fun toggleAdblock() {
		withMainContext {
			getSettings().browserEnableAdblocker = !getSettings().browserEnableAdblocker
			getSettings().updateInDB()
			updateToggleIcon(txtEnableAdblock, getSettings().browserEnableAdblocker)
			logger.d("Web Adblock enabled: ${getSettings().browserEnableAdblocker}")
		}
	}

	/**
	 * Toggles the automatic loading of images on web pages.
	 *
	 * Modifies the browserEnableImages setting and updates the database. This
	 * is typically used to reduce data consumption or increase page load
	 * performance. The UI icon is synchronized immediately after the change.
	 */
	private suspend fun toggleShowImages() {
		withMainContext {
			getSettings().browserEnableImages = !getSettings().browserEnableImages
			getSettings().updateInDB()
			updateToggleIcon(txtShowImageOnWeb, getSettings().browserEnableImages)
			logger.d("Show web images enabled: ${getSettings().browserEnableImages}")
		}
	}

	/**
	 * Toggles the background video detection and grabber utility.
	 *
	 * Updates the browserEnableVideoGrabber configuration in the database. When
	 * enabled, the browser engine actively monitors pages for downloadable
	 * media streams. The UI reflects the change via the toggle icon.
	 */
	private suspend fun toggleVideoGrabber() {
		withMainContext {
			getSettings().browserEnableVideoGrabber = !getSettings().browserEnableVideoGrabber
			getSettings().updateInDB()
			updateToggleIcon(txtEnableVideoGrabber, getSettings().browserEnableVideoGrabber)
			logger.d("Video grabber enabled: ${getSettings().browserEnableVideoGrabber}")
		}
	}

	/**
	 * Toggles the suppression of unwanted web pop-up windows.
	 *
	 * Flips the browserEnablePopupBlocker boolean and persists the state. This
	 * helps maintain a clean browsing experience by preventing intrusive windows.
	 * The UI is updated on the Main thread to ensure visual consistency.
	 */
	private suspend fun togglePopupBlocker() {
		withMainContext {
			getSettings().browserEnablePopupBlocker = !getSettings().browserEnablePopupBlocker
			getSettings().updateInDB()
			updateToggleIcon(txtEnablePopupBlocker, getSettings().browserEnablePopupBlocker)
			logger.d("Popup blocker enabled: ${getSettings().browserEnablePopupBlocker}")
		}
	}

	/**
	 * Toggles the browser's desktop rendering mode.
	 *
	 * Flips the current browserDesktopBrowsing state and persists the change to the
	 * local database. This triggers a UI update to the corresponding toggle icon
	 * and logs the new state for debugging purposes.
	 */
	private suspend fun toggleDesktopMode() {
		withMainContext {
			getSettings().browserDesktopBrowsing = !getSettings().browserDesktopBrowsing
			getSettings().updateInDB()
			updateToggleIcon(txtEnableDesktopMode, getSettings().browserDesktopBrowsing)
			logger.d("Desktop mode enabled: ${getSettings().browserDesktopBrowsing}")
		}
	}

	/**
	 * Toggles the browser's private (incognito) browsing mode.
	 *
	 * Inverts the browserPrivateBrowsing setting, ensuring the state is saved
	 * to the database and the UI reflects the change immediately. This mode
	 * typically prevents the storage of history and cookies during web sessions.
	 */
	private suspend fun togglePrivateMode() {
		withMainContext {
			getSettings().browserPrivateBrowsing = !getSettings().browserPrivateBrowsing
			getSettings().updateInDB()
			updateToggleIcon(txtEnablePrivateMode, getSettings().browserPrivateBrowsing)
			logger.d("Private web mode enabled: ${getSettings().browserPrivateBrowsing}")
		}
	}

	/**
	 * Displays the editor dialog for selecting or customizing the HTTP User Agent.
	 *
	 * Initializes the [WebHttpUserAgentSelector] with the current activity context
	 * and settings. An application listener is attached to handle the logic
	 * once the user confirms their selection, ensuring the dialog is shown
	 * safely on the Main thread.
	 */
	private suspend fun showUserAgentEditor() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				val dialog = WebHttpUserAgentSelector(activityRef, getSettings())
					.apply {
						onApplyListener = {
							onUserAgentSettingsApplied(activityRef)
						}
					}
				withMainContext { dialog.show() }
			}
		}
	}

	/**
	 * Callback invoked when the user selects and applies a new HTTP User Agent string.
	 *
	 * This method launches a coroutine to persist the updated configuration to the
	 * database and refreshes the UI toggle states to reflect any cascading changes.
	 * A confirmation toast is displayed to provide immediate visual feedback.
	 *
	 * @param activityRef A strong reference to the current activity context.
	 */
	private fun onUserAgentSettingsApplied(
		activityRef: AdvBrowserSettingsActivity
	) {
		activityRef.activityCoroutineScope.launch {
			showToast(activityRef, R.string.title_setting_applied)
			getSettings().updateInDB()
			updateAllToggleStates()
		}
	}

	/**
	 * Displays a confirmation dialog before initiating a browser history and cache wipe.
	 *
	 * Utilizes [MsgDialogUtils] to construct a warning prompt. The positive action
	 * is wrapped in an [activityCoroutineScope] launch to allow the transition
	 * from the view's click callback to the suspendable [clearBrowserCache] logic
	 * while ensuring the dialog closes on the Main thread.
	 */
	private suspend fun showClearCacheDialog() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				MsgDialogUtils.getMessageDialog(
					baseActivityInf = activityRef,
					isTitleVisible = true,
					isNegativeButtonVisible = false,
					titleTextViewCustomize = { it.setText(R.string.title_clear_browser_history) },
					messageTextViewCustomize = { it.setText(R.string.text_clear_browser_history_warning) },
					positiveButtonTextCustomize = {
						it.setText(R.string.title_clear_web_history)
						it.setLeftSideDrawable(R.drawable.ic_button_clear)
					}
				)?.apply {
					setOnClickForPositiveButton {
						activityCoroutineScope.launch {
							withMainContext { close() }
							clearBrowserCache()
						}
					}
					withMainContext { show() }
				}
			}
		}
	}

	/**
	 * Orchestrates the permanent removal of locally stored browser data and history.
	 *
	 * This method executes a two-stage cleanup process on the [Dispatchers.IO] thread:
	 * 1. **Filesystem**: Recursively deletes the application's internal cache directory
	 * to free up storage space.
	 * 2. **Database**: Retrieves history-specific record IDs and performs a batch
	 * removal via ObjectBox to ensure atomic data deletion.
	 *
	 * Feedback is delivered to the user via Main-thread toasts regardless of the
	 * outcome, ensuring the UI remains responsive and informed.
	 */
	private suspend fun clearBrowserCache() {
		withIOContext {
			safeActivityRef?.let { activityRef ->
				try {
					activityRef.cacheDir.deleteRecursively()
					val historyRecordsIds = getHistoryRecordsIds().toList()
					getWebRecordsBox().removeByIds(historyRecordsIds)
					logger.d("Browser history cache cleared successfully")
					withMainContext {
						val toastMsg = getString(R.string.title_cache_cleared_successfully)
						showToast(activityRef, toastMsg)
					}
				} catch (error: Exception) {
					logger.e("Failed to clear browser history cache", error)
					withMainContext {
						val toastMsg = getString(R.string.title_failed_to_clear_cache_try_again_later)
						showToast(activityRef, toastMsg)
					}
				}
			}
		}
	}

	/**
	 * Displays a confirmation dialog before resetting all browser configurations.
	 *
	 * This method uses [MsgDialogUtils] to create a non-interruptible warning prompt.
	 * Upon confirming the reset, it launches a new coroutine within the activity
	 * scope to perform the database updates via [resetBrowserSettings].
	 * * Note: The dialog closure and initial show command are both explicitly
	 * dispatched to the Main thread to ensure UI stability.
	 */
	private suspend fun showResetSettingsDialog() {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				val dialog = MsgDialogUtils.getMessageDialog(
					baseActivityInf = activityRef,
					isTitleVisible = true,
					isNegativeButtonVisible = false,
					titleTextViewCustomize = { it.setText(R.string.title_reset_browser_settings) },
					messageTextViewCustomize = { it.setText(R.string.text_reset_browser_settings) },
					negativeButtonTextCustomize = { it.setText(R.string.title_cancel) },
					positiveButtonTextCustomize = {
						it.setText(R.string.title_reset_now)
						it.setLeftSideDrawable(R.drawable.ic_button_clear)
					}
				)

				dialog?.setOnClickForPositiveButton {
					activityCoroutineScope.launch {
						withMainContext { dialog.close() }
						resetBrowserSettings()
					}
				}

				withMainContext {
					dialog?.show()
				}
			}
		}
	}

	/**
	 * Resets all browser-related configurations to their factory default values.
	 *
	 * This operation performs a batch update of the settings object within a [withIOContext]
	 * block to ensure database integrity and Main-thread fluidity. It restores defaults
	 * for privacy, content filtering, and identifying strings (User Agent).
	 * * Upon success: Syncs the UI toggles and displays a confirmation toast.
	 * * Upon failure: Triggers haptic feedback and logs the exception for debugging.
	 */
	private suspend fun resetBrowserSettings() {
		withIOContext {
			runCatching {
				getSettings().apply {
					val defaultHomepage = getString(R.string.text_https_google_com)
					browserDefaultHomepage = defaultHomepage
					browserPrivateBrowsing = false
					browserDesktopBrowsing = false
					browserEnableAdblocker = true
					browserEnableJavascript = true
					browserEnableImages = true
					browserEnablePopupBlocker = true
					browserEnableVideoGrabber = true
					val resId = R.string.text_browser_default_mobile_http_user_agent
					browserHttpUserAgent = getString(resId)
					updateInDB()
				}

				updateAllToggleStates()
				val msgId = R.string.title_browser_settings_reset_to_defaults
				showToast(safeActivityRef, msgId)
				logger.d("Browser settings reset to defaults")
			}.onFailure { error ->
				safeActivityRef?.doSomeVibration()
				showToast(safeActivityRef, R.string.title_something_went_wrong)
				logger.e("Failed to reset browser settings", error)
			}
		}
	}
}