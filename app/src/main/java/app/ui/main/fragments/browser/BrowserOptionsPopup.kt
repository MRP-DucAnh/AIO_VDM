package app.ui.main.fragments.browser

import android.content.Intent
import android.content.Intent.*
import android.view.View
import androidx.core.net.toUri
import app.core.AIOApp.Companion.aioBookmark
import app.core.AIOApp.Companion.aioSettings
import app.core.engines.browser.bookmarks.AIOBookmark
import app.core.engines.video_parser.dialogs.VideoLinkPasteEditor
import app.ui.main.MotherActivity
import app.ui.main.fragments.browser.activities.BookmarksActivity
import app.ui.main.fragments.browser.activities.HistoryActivity
import app.ui.main.fragments.settings.activities.browser.AdvBrowserSettingsActivity
import app.ui.main.guides.GuidePlatformPicker
import app.ui.others.information.UserFeedbackActivity
import com.aio.R
import lib.networks.URLUtility.ensureHttps
import lib.networks.URLUtility.isValidURL
import lib.networks.URLUtilityKT.normalizeEncodedUrl
import lib.process.LogHelperUtils
import lib.texts.ClipboardUtils.copyTextToClipboard
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.hideOnScreenKeyboard
import lib.ui.builders.PopupBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.util.Date

/**
 * Browser Options Popup Manager
 *
 * A specialized popup menu controller that provides comprehensive browser-related
 * actions and utilities within the browser fragment. This class manages the
 * contextual menu that appears when users access browser options, offering
 * navigation controls, bookmark management, sharing capabilities, and access
 * to various browser features.
 *
 * ## Key Responsibilities:
 * - Display and manage the browser options popup menu
 * - Handle web navigation controls (back/forward)
 * - Manage bookmarks and browser history access
 * - Facilitate URL sharing and copying
 * - Provide access to browser settings and help guides
 * - Handle system browser integration
 *
 * ## Architecture:
 * - Uses PopupBuilder for consistent popup UI rendering
 * - Maintains safe references to parent activity and fragment
 * - Centralized event handling for all popup actions
 * - Comprehensive error handling with user feedback
 *
 * ## UI Integration:
 * - Anchored to the browser's options button in the top bar
 * - Automatically hides keyboard when displayed
 * - Closes side navigation if open to prevent conflicts
 * - Provides haptic feedback for user interactions
 *
 * @param browserFragment The parent BrowserFragment instance that owns this popup
 * @see BrowserFragment For the parent fragment context
 * @see PopupBuilder For the underlying popup rendering engine
 * @since Version 1.0.0
 */
class BrowserOptionsPopup(val browserFragment: BrowserFragment) {

	/**
	 * Logger instance for tracking popup interactions and debugging.
	 * Captures user actions, error conditions, and performance metrics for analytics.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Safe reference to the parent MotherActivity.
	 *
	 * This property provides null-safe access to the hosting activity while
	 * preventing memory leaks through proper fragment-activity lifecycle
	 * management. Returns null if the activity is no longer available.
	 */
	private val safeMotherActivityRef
		get() = browserFragment.safeBaseActivityRef as? MotherActivity

	/**
	 * Popup builder instance responsible for rendering and managing the popup UI.
	 *
	 * This lateinit property is initialized in the setupPopupBuilder() method
	 * during class construction to ensure proper resource loading.
	 */
	private lateinit var popupBuilder: PopupBuilder

	/**
	 * Initialization block that configures the popup upon instance creation.
	 *
	 * This block ensures the popup is ready for display by:
	 * 1. Setting up the popup builder with layout and anchor configuration
	 * 2. Binding click events to all popup menu items
	 *
	 * The popup remains hidden until explicitly shown via the show() method.
	 */
	init {
		setupPopupBuilder()
		setupClickEvents()
	}

	/**
	 * Displays the browser options popup menu.
	 *
	 * This method orchestrates the complete popup display workflow:
	 * 1. Hides the on-screen keyboard to prevent input conflicts
	 * 2. Closes the side navigation drawer if open
	 * 3. Displays the popup anchored to the options button
	 *
	 * The popup is positioned relative to the anchor view defined in setupPopupBuilder().
	 *
	 * @see hideOnScreenKeyboard For keyboard management
	 */
	fun show() {
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webviewEngine = browserFragmentBody.webviewEngine
		val focusedView = webviewEngine.currentWebView
		hideOnScreenKeyboard(safeMotherActivityRef, focusedView)
		safeMotherActivityRef?.sideNavigation?.closeDrawerNavigation()
		popupBuilder.show()
	}

	/**
	 * Closes the currently displayed popup menu.
	 *
	 * This method dismisses the popup if it's currently visible. It's safe to
	 * call even if the popup is already closed or was never shown.
	 */
	fun close() {
		popupBuilder.close()
	}

	/**
	 * Initializes the PopupBuilder with layout and anchor configuration.
	 *
	 * Configures the popup with:
	 * - The specific layout resource for browser options
	 * - The anchor view (options button in browser top bar)
	 * - Parent activity context for resource resolution
	 *
	 * This method must be called before attempting to show the popup.
	 */
	private fun setupPopupBuilder() {
		popupBuilder = PopupBuilder(
			activityInf = safeMotherActivityRef,
			popupLayoutId = R.layout.frag_brow_1_top_popup_1,
			popupAnchorView = browserFragment.browserFragmentTop.webviewOptionPopup
		)
	}

	/**
	 * Binds click listeners to all interactive elements in the popup.
	 *
	 * This method creates a mapping between view IDs and their corresponding
	 * action methods, then attaches click listeners to each view. Using a
	 * declarative mapping approach makes the code more maintainable and
	 * easier to extend with new actions.
	 *
	 * The mapping includes 14 different browser actions covering navigation,
	 * bookmark management, sharing, and access to other browser features.
	 */
	private fun setupClickEvents() {
		popupBuilder.getPopupView().apply {
			val clickActions = mapOf(
				findViewById<View>(R.id.btn_webpage_back) to { goToPreviousWebpage() },
				findViewById<View>(R.id.btn_webpage_forward) to { goToNextWebpage() },
				findViewById<View>(R.id.btn_save_bookmark) to { saveCurrentWebpageAsBookmark() },
				findViewById<View>(R.id.btn_make_default_homepage) to { setAsDefaultHomepage() },
				findViewById<View>(R.id.btn_share_webpage_url) to { shareCurrentWebpageURL() },
				findViewById<View>(R.id.btn_copy_webpage_url) to { copyCurrentWebpageURL() },
				findViewById<View>(R.id.btn_open_with_system_browser) to { openWebpageInSystemBrowser() },
				findViewById<View>(R.id.btn_add_download_task) to { openNewDownloadTaskEditor() },
				findViewById<View>(R.id.btn_open_bookmark) to { openBookmarkActivity() },
				findViewById<View>(R.id.btn_open_history) to { openHistoryActivity() },
				findViewById<View>(R.id.btn_open_web_settings) to { openBrowserSettings() },
				findViewById<View>(R.id.btn_how_to_use) to { openHowToDownload() },
				findViewById<View>(R.id.btn_open_feedback) to { openFeedbackActivity() }
			)

			clickActions.forEach { (view, action) ->
				view.setOnClickListener { action() }
			}
		}
	}

	/**
	 * Navigates the webview to the previous page in browsing history.
	 *
	 * This action mimics the browser's back button functionality. If no
	 * previous page exists in the history stack, provides haptic feedback
	 * and displays a user-friendly message.
	 *
	 * The popup automatically closes before navigation to prevent visual
	 * interference with the page transition.
	 */
	private fun goToPreviousWebpage() {
		close()
		val webviewEngine = browserFragment.getBrowserWebEngine()
		webviewEngine.currentWebView?.let { webView ->
			if (webView.canGoBack()) {
				webView.goBack()
			} else {
				safeMotherActivityRef?.doSomeVibration(20)
				showToast(safeMotherActivityRef, R.string.title_reached_limit_for_going_back)
			}
		}
	}

	/**
	 * Navigates the webview to the next page in browsing history.
	 *
	 * This action mimics the browser's forward button functionality. If no
	 * forward page exists in the history stack, provides haptic feedback
	 * and displays a user-friendly message.
	 *
	 * The popup automatically closes before navigation to prevent visual
	 * interference with the page transition.
	 */
	private fun goToNextWebpage() {
		close()
		browserFragment.getBrowserWebEngine().currentWebView?.let { webView ->
			if (webView.canGoForward()) {
				webView.goForward()
			} else {
				safeMotherActivityRef?.doSomeVibration()
				showToast(safeMotherActivityRef, R.string.title_reached_limit_for_going_forward)
			}
		}
	}

	/**
	 * Saves the current webpage as a bookmark.
	 *
	 * This method creates a new bookmark entry with:
	 * - Current page URL (normalized and encoded)
	 * - Current page title (or "Unknown" if title is unavailable)
	 * - Current timestamp for creation and modification dates
	 *
	 * The bookmark is added to the beginning of the bookmark library for
	 * quick access and immediately persisted to storage.
	 *
	 * @throws Exception If bookmark storage operations fail
	 * @see AIOBookmark For the bookmark data structure
	 * @see aioBookmark For bookmark storage management
	 */
	private fun saveCurrentWebpageAsBookmark() {
		close()
		try {
			val currentWebpageUrl = getCurrentWebpageURL()
			val currentWebpageTitle = getCurrentWebpageTitle()
			if (currentWebpageUrl.isNullOrEmpty()) {
				safeMotherActivityRef?.doSomeVibration()
				showToast(safeMotherActivityRef, R.string.title_webpage_url_invalid)
				return
			}
			aioBookmark.getBookmarkLibrary().add(0, AIOBookmark().apply {
				bookmarkCreationDate = Date()
				bookmarkModifiedDate = Date()
				bookmarkUrl = normalizeEncodedUrl(currentWebpageUrl)
				bookmarkName = if (currentWebpageTitle.isNullOrEmpty())
					getText(R.string.title_unknown) else currentWebpageTitle
			})

			aioBookmark.updateInStorage()
			showToast(safeMotherActivityRef, R.string.title_bookmark_saved)
		} catch (error: Exception) {
			error.printStackTrace()
			safeMotherActivityRef?.doSomeVibration()
			showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
		}
	}

	/**
	 * Sets the current webpage as the default homepage.
	 *
	 * This action allows users to personalize their browsing experience by
	 * setting any webpage as their default startup page. The method:
	 * 1. Validates the current URL is accessible and valid
	 * 2. Ensures HTTPS is used when available for security
	 * 3. Updates the persistent browser settings
	 * 4. Provides confirmation feedback to the user
	 *
	 * @throws Exception If URL validation or settings storage fails
	 */
	private fun setAsDefaultHomepage() {
		try {
			close()
			val activity = safeMotherActivityRef ?: run {
				logger.d("Cannot set homepage - activity reference is null")
				return
			}

			val currentUrl = getCurrentWebpageURL()?.takeIf { it.isNotEmpty() } ?: return
			logger.d("User attempting to set homepage: $currentUrl")

			if (!isValidURL(currentUrl)) {
				logger.d("Invalid homepage URL entered: $currentUrl")
				activity.doSomeVibration()
				showToast(activity, R.string.title_invalid_url)
				return
			}

			val finalUrl = ensureHttps(currentUrl) ?: currentUrl
			aioSettings.browserDefaultHomepage = finalUrl
			aioSettings.updateInDB()

			logger.d("Homepage successfully updated to: $finalUrl")
			showToast(activity, R.string.title_updated_successfully)
		} catch (e: Exception) {
			showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
			safeMotherActivityRef?.doSomeVibration()
			logger.e("Exception while setting default homepage", e)
		}
	}

	/**
	 * Opens a system share dialog to share the current webpage URL.
	 *
	 * This method leverages Android's native sharing capabilities to allow
	 * users to share the current URL via any installed sharing app (email,
	 * messaging, social media, etc.). The share intent includes:
	 * - Plain text MIME type for maximum compatibility
	 * - The current webpage URL as shareable content
	 * - A user-friendly chooser title
	 *
	 * @throws Exception If the share intent cannot be created or launched
	 */
	private fun shareCurrentWebpageURL() {
		close()
		try {
			val currentWebviewUrl = getCurrentWebpageURL()
			if (currentWebviewUrl == null) {
				safeMotherActivityRef?.doSomeVibration()
				showToast(safeMotherActivityRef, R.string.title_webpage_url_invalid)
				return
			}

			val shareIntent = Intent().apply {
				action = ACTION_SEND
				putExtra(EXTRA_TEXT, currentWebviewUrl)
				type = "text/plain"
			}

			safeMotherActivityRef?.startActivity(
				createChooser(
					shareIntent,
					safeMotherActivityRef?.getString(R.string.title_share_with_others)
				)
			)
		} catch (error: Exception) {
			error.printStackTrace()
			safeMotherActivityRef?.doSomeVibration()
			showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
		}
	}

	/**
	 * Copies the current webpage URL to the system clipboard.
	 *
	 * This action allows users to quickly copy URLs for pasting elsewhere.
	 * The method validates the URL exists before attempting to copy and
	 * provides visual confirmation when successful.
	 *
	 * @throws Exception If clipboard access is denied or fails
	 */
	private fun copyCurrentWebpageURL() {
		close()
		try {
			val currentWebpageUrl = getCurrentWebpageURL()
			if (currentWebpageUrl.isNullOrEmpty()) {
				safeMotherActivityRef?.doSomeVibration()
				showToast(safeMotherActivityRef, R.string.title_webpage_url_invalid)
				return
			}

			copyTextToClipboard(safeMotherActivityRef, currentWebpageUrl)
			showToast(safeMotherActivityRef, R.string.title_copied_to_clipboard)
		} catch (error: Exception) {
			error.printStackTrace()
			safeMotherActivityRef?.doSomeVibration()
			showToast(safeMotherActivityRef, R.string.title_something_went_wrong)
		}
	}

	/**
	 * Opens the current URL in the device's default system browser.
	 *
	 * This provides an escape hatch for pages that may not render correctly
	 * in the in-app browser or for users who prefer their system browser.
	 * The method:
	 * 1. Validates the current URL is accessible
	 * 2. Creates a VIEW intent with proper task flags
	 * 3. Handles cases where no browser app is installed
	 *
	 * @throws Exception If no browser app can handle the URL
	 */
	private fun openWebpageInSystemBrowser() {
		try {
			val fileUrl: String? = getCurrentWebpageURL()
			if (!fileUrl.isNullOrEmpty() && isValidURL(fileUrl)) {
				val intent = Intent(ACTION_VIEW, fileUrl.toUri()).apply {
					addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
				}; safeMotherActivityRef?.startActivity(intent)
			} else showInvalidUrlToast()
		} catch (err: Exception) {
			err.printStackTrace()
			showToast(safeMotherActivityRef, R.string.title_please_install_web_browser)
		}
	}

	/**
	 * Displays a toast message indicating an invalid URL.
	 *
	 * This helper method provides consistent user feedback when URL-related
	 * operations fail due to invalid or empty URLs.
	 */
	private fun showInvalidUrlToast() {
		showToast(safeMotherActivityRef, R.string.title_invalid_url)
	}

	/**
	 * Opens the video link paste editor for manual download task creation.
	 *
	 * This action provides direct access to the download manager from the
	 * browser context, allowing users to quickly add download tasks without
	 * navigating away from their current browsing session.
	 */
	private fun openNewDownloadTaskEditor() {
		safeMotherActivityRef?.let { activityRef ->
			close().let { VideoLinkPasteEditor(activityRef).show() }
		}
	}

	/**
	 * Displays the platform picker guide for video downloading instructions.
	 *
	 * This help guide assists users in understanding how to download videos
	 * from various supported platforms, providing platform-specific instructions
	 * and best practices.
	 */
	private fun openHowToDownload() {
		close()
		GuidePlatformPicker(safeMotherActivityRef).show()
	}

	/**
	 * Launches the BookmarksActivity for browsing and managing saved bookmarks.
	 *
	 * Opens the full bookmark management interface using the activity result
	 * launcher pattern, allowing for potential bookmark selection and return
	 * to the current browser session.
	 */
	private fun openBookmarkActivity() {
		close()
		val input = Intent(safeMotherActivityRef, BookmarksActivity::class.java)
		safeMotherActivityRef?.resultLauncher?.launch(input)
	}

	/**
	 * Launches the HistoryActivity for browsing and managing browsing history.
	 *
	 * Opens the browsing history interface using the activity result launcher
	 * pattern, allowing users to revisit previously viewed pages.
	 */
	private fun openHistoryActivity() {
		close()
		val input = Intent(safeMotherActivityRef, HistoryActivity::class.java)
		safeMotherActivityRef?.resultLauncher?.launch(input)
	}

	/**
	 * Opens the UserFeedbackActivity for submitting user feedback and suggestions.
	 *
	 * Provides a direct channel for users to report issues, suggest features,
	 * or provide general feedback about the browser experience.
	 */
	private fun openFeedbackActivity() {
		close()
		safeMotherActivityRef?.openActivity(UserFeedbackActivity::class.java, true)
	}

	/**
	 * Opens the Advanced Browser Settings activity for configuration.
	 *
	 * Provides access to comprehensive browser settings including privacy options,
	 * performance tweaks, and interface customization.
	 */
	private fun openBrowserSettings() {
		close()
		safeMotherActivityRef?.openActivity(AdvBrowserSettingsActivity::class.java, true)
	}

	/**
	 * Retrieves the URL of the currently displayed webpage.
	 *
	 * This method provides null-safe access to the webview's current URL,
	 * returning null if no webview is available or no page is loaded.
	 *
	 * @return The current webpage URL or null if unavailable
	 */
	private fun getCurrentWebpageURL(): String? {
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webviewEngine = browserFragmentBody.webviewEngine
		val url = webviewEngine.currentWebView?.url
		return url
	}

	/**
	 * Retrieves the title of the currently displayed webpage.
	 *
	 * This method provides null-safe access to the webview's current page title,
	 * returning null if no webview is available or no title is set.
	 *
	 * @return The current webpage title or null if unavailable
	 */
	private fun getCurrentWebpageTitle(): String? {
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webviewEngine = browserFragmentBody.webviewEngine
		val title = webviewEngine.currentWebView?.title
		return title
	}
}
