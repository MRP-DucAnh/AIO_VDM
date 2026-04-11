package app.ui.main.fragments.browser.webengine

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.aioHistory
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.BaseActivity
import app.core.engines.browser.history.HistoryModel
import app.core.engines.caches.UserCookieStore
import app.ui.main.MotherActivity
import com.aio.R
import com.bumptech.glide.Glide
import lib.networks.URLUtilityKT.normalizeEncodedUrl
import lib.process.AsyncJobUtils.executeInBackground
import lib.process.AsyncJobUtils.executeOnMainThread
import lib.process.ThreadsUtility
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.closeAnyAnimation
import java.io.File
import java.util.Date

/**
 * Browser WebChrome Client
 *
 * A comprehensive implementation of Android's [WebChromeClient] that provides
 * advanced browser event handling for the AIO application's WebView engine.
 * This client serves as the primary bridge between native Android components
 * and web content, handling everything from basic progress updates to complex
 * full-screen video playback and file uploads.
 *
 * ## Key Responsibilities:
 * - Page lifecycle event handling (progress, titles, icons)
 * - Popup window creation and management
 * - Full-screen video playback with custom view handling
 * - File chooser integration for web-based file uploads
 * - Browser history and bookmark integration
 * - Cookie persistence for session management
 *
 * ## Architecture:
 * - Tightly coupled with [WebViewEngine] for coordinated browser management
 * - Implements the WebChromeClient interface with comprehensive override methods
 * - Manages custom view states for full-screen content
 * - Provides thread-safe operations with coroutine and background task execution
 *
 * ## Event Flow:
 * 1. WebView triggers events (page load, file chooser, etc.)
 * 2. BrowserWebChromeClient intercepts and processes events
 * 3. Updates UI components via WebViewEngine
 * 4. Persists data (history, cookies) as needed
 * 5. Provides user feedback through visual indicators
 *
 * @property webviewEngine The parent WebViewEngine that owns this client instance
 * @see WebChromeClient For the base Android interface
 * @see WebViewEngine For the parent browser engine
 * @since Version 1.0.0
 */
class BrowserWebChromeClient(val webviewEngine: WebViewEngine) : WebChromeClient() {

	/**
	 * Callback for handling file upload results from web-based file choosers.
	 *
	 * This callback is stored when a web page triggers a file upload dialog
	 * and is used to communicate the user's file selection back to the WebView.
	 * The callback is cleared after each use to prevent memory leaks.
	 */
	private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

	/**
	 * Currently displayed custom full-screen view container.
	 *
	 * Used primarily for HTML5 video full-screen playback. This view wraps
	 * the actual video content and provides proper layout management during
	 * full-screen mode transitions.
	 */
	private var customView: View? = null

	/**
	 * Active video view instance during full-screen playback.
	 *
	 * Contains the actual video rendering surface. This reference is maintained
	 * separately from customView to allow for proper transformation management
	 * (rotation, scaling) during landscape full-screen playback.
	 */
	private var videoView: View? = null

	/**
	 * Callback for notifying the WebView when custom view should be hidden.
	 *
	 * Provided by the WebView system and called when exiting full-screen mode.
	 * Ensures proper cleanup and restoration of the normal browsing interface.
	 */
	private var customViewCallback: CustomViewCallback? = null

	/**
	 * Handles page loading progress updates.
	 *
	 * This method is called frequently during page load (approximately every
	 * 100ms) to provide real-time progress feedback. It validates that the
	 * progress update belongs to the currently active WebView before updating
	 * UI elements to prevent cross-talk between multiple browser tabs.
	 *
	 * ## Implementation Details:
	 * - Progress ranges from 0 to 100 (percentage)
	 * - Only processes updates for the currentWebView to avoid tab confusion
	 * - Delegates UI updates to WebViewEngine for consistency
	 * - Called on the main thread by WebView system
	 *
	 * @param webView The WebView whose loading progress changed
	 * @param progress Current load progress (0-100)
	 */
	override fun onProgressChanged(webView: WebView?, progress: Int) {
		// Ensure we only update progress for the currently active WebView
		if (webviewEngine.currentWebView != webView) return

		// Update UI elements (progress bar, etc.) via WebViewEngine
		webviewEngine.updateWebViewProgress(webView, progress)
	}

	/**
	 * Handles creation of new browser windows (popups, tabs).
	 *
	 * This method intercepts JavaScript window.open() calls and link targets
	 * that request new windows. It provides configurable popup blocking based
	 * on user preferences while still allowing legitimate popups to function.
	 *
	 * ## Behavior:
	 * 1. Validates the request originates from the current WebView
	 * 2. Creates a temporary WebView to capture the popup content
	 * 3. Checks popup blocker settings
	 * 4. Either blocks the popup or adds it as a new browser tab
	 *
	 * ## Security Considerations:
	 * - Uses temporary WebView to isolate potentially malicious content
	 * - Respects user's popup blocking preferences
	 * - Provides user feedback when popups are blocked
	 *
	 * @param view The originating WebView
	 * @param isDialog Whether the new window should be a dialog
	 * @param isUserGesture True if initiated by user action
	 * @param resultMsg Message containing transport for the new WebView
	 * @return True if the window creation was handled
	 */
	override fun onCreateWindow(
		view: WebView?,
		isDialog: Boolean,
		isUserGesture: Boolean,
		resultMsg: Message?
	): Boolean {
		// Only handle the request if it's from the currently active WebView
		if (webviewEngine.currentWebView != view) return false

		// Extract the transport object from the message to attach a new WebView
		val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

		// Create a temporary WebView to handle the new content
		val tempWebView = webviewEngine.generateNewWebview() as WebView
		tempWebView.apply {
			layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

			// Temporary WebView client to capture the page finish event
			webViewClient = object : WebViewClient() {
				override fun onPageFinished(view: WebView, url: String) {
					// Once loaded, add the new tab to the browser UI and remove the temp WebView
					webviewEngine.safeMotherActivityRef.sideNavigation
						?.addNewBrowsingTab(url, webviewEngine)
					(parent as? ViewGroup)?.removeView(this@apply)
				}
			}
		}

		// Assign the new WebView to the transport for WebView handling
		transport.webView = tempWebView

		// Check popup blocker setting before allowing the popup to load
		if (!aioSettings.browserEnablePopupBlocker) {
			resultMsg.sendToTarget() // Allow popup
		} else {
			// Block popup and show user feedback
			val messageResId = getText(R.string.title_blocked_unwanted_popup_links)
			webviewEngine.showQuickBrowserInfo(messageResId)
		}

		return true
	}

	/**
	 * Handles website favicon (icon) reception.
	 *
	 * This method processes favicon bitmaps received from loaded web pages.
	 * It updates the browser's favicon display and persists cookies for the
	 * current domain to maintain session state across application restarts.
	 *
	 * ## Implementation Details:
	 * - Updates favicon only for the currently active WebView
	 * - Shows loading animation during favicon processing
	 * - Uses Glide for efficient bitmap loading and caching
	 * - Persists cookies in background for session continuity
	 *
	 * @param webview The WebView that received the icon
	 * @param icon The favicon bitmap (may be null)
	 */
	override fun onReceivedIcon(webview: WebView?, icon: Bitmap?) {
		super.onReceivedIcon(webview, icon)
		try {
			val motherActivity = webview?.context as? MotherActivity ?: return
			val totalWebViews = motherActivity.sideNavigation?.totalWebViews
			persistCookies(webview.url, motherActivity)

			totalWebViews?.forEach { webView ->
				// Only update the icon for the currently active WebView
				if ((webviewEngine.currentWebView?.url == webview.url) && (icon != null)) {
					// Start loading animation for the favicon
					val browserFragmentTop = webviewEngine.browserFragment.browserFragmentTop
					browserFragmentTop.animateDefaultFaviconLoading(shouldStopAnimation = true)

					// Load the favicon bitmap into the UI
					val webViewFavicon = browserFragmentTop.webViewFavicon
					Glide.with(webview.context).load(icon).into(webViewFavicon)
				}
			}
		} catch (error: Exception) {
			error.printStackTrace()
		}
	}

	/**
	 * Handles webpage title updates with comprehensive browser integration.
	 *
	 * This method orchestrates multiple browser functions when a page title
	 * is received:
	 * 1. Updates browser UI with new title
	 * 2. Manages favicon display (cached or default)
	 * 3. Records browsing history
	 * 4. Persists cookies for session management
	 *
	 * ## Thread Management:
	 * - UI updates execute on main thread
	 * - Favicon lookup and file operations run in background
	 * - History operations are performed synchronously
	 * - Cookie persistence uses coroutines on IO dispatcher
	 *
	 * ## Safety Features:
	 * - Validates event belongs to current WebView
	 * - Try-catch blocks prevent crashes from unexpected data
	 * - Null-safe operations throughout
	 *
	 * @param webView The WebView sending the title update
	 * @param title The new page title (may be null)
	 */
	override fun onReceivedTitle(webView: WebView?, title: String?) {
		// Only handle the event if it belongs to the current WebView
		if (webviewEngine.currentWebView != webView) return

		try {
			// Get a reference to the hosting MotherActivity
			val motherActivity = webView?.context as? MotherActivity ?: return
			persistCookies(webView.url, motherActivity)

			// Process the title only if it is not null
			title?.let { titleText ->
				// Update the browser UI with the new title
				motherActivity.browserFragment?.let { browserFragment ->
					browserFragment.browserFragmentTop.webviewTitle.text = titleText
					motherActivity.sideNavigation?.webTabListAdapter?.notifyDataSetChanged()

					val correspondingWebViewUrl = webView.url.toString()

					// Fetch favicon in a background thread
					executeInBackground {
						// Show favicon loading animation
						executeOnMainThread {
							browserFragment.browserFragmentTop.animateDefaultFaviconLoading(true)
						}

						// Try to get the cached favicon for the current page
						val faviconCachedPath = aioFavicons.getFavicon(correspondingWebViewUrl)

						if (!faviconCachedPath.isNullOrEmpty()) {
							val faviconImg = File(faviconCachedPath)

							// If cached favicon exists, set it
							if (faviconImg.exists()) {
								executeOnMainThread {
									browserFragment.browserFragmentTop.webViewFavicon.let { faviconViewer ->
										faviconViewer.setImageURI(Uri.fromFile(faviconImg))
										closeAnyAnimation(faviconViewer)
									}
								}
							} else {
								// If no cached favicon, load a default icon
								executeOnMainThread {
									val defaultFaviconResId = R.drawable.ic_button_browser_favicon
									browserFragment.browserFragmentTop.webViewFavicon.let { faviconViewer ->
										Glide.with(motherActivity).load(defaultFaviconResId).into(faviconViewer)
										closeAnyAnimation(faviconViewer)
									}
								}
							}
						}
					}
				}

				// Save the visit to browsing history if it's a real page
				if (webView.url.toString() != "about:blank") {
					aioHistory.getHistoryLibrary().apply {
						// Remove old entry if the same URL already exists
						val existingEntryIndex = indexOfFirst { it.historyUrl == webView.url.toString() }
						if (existingEntryIndex != -1) removeAt(existingEntryIndex)
					}

					// Add the new entry at the top
					aioHistory.getHistoryLibrary().add(0, HistoryModel().apply {
						historyUserAgent = webView.settings.userAgentString.toString()
						historyVisitDateTime = Date()
						historyUrl = normalizeEncodedUrl(webView.url.toString())
						historyTitle = titleText
					})

					// Persist history changes
					aioHistory.updateInStorage()
				}
			}
		} catch (error: Exception) {
			// Prevents crashes from casting errors or null references
			error.printStackTrace()
		}
	}

	/**
	 * Handles file chooser requests from web content (<input type="file">).
	 *
	 * This method integrates with the Android scoped storage system to provide
	 * secure file selection capabilities to web pages. It manages the complete
	 * file selection workflow including permission handling and result delivery.
	 *
	 * ## Workflow:
	 * 1. Cancel any pending file chooser callback
	 * 2. Store new callback for result delivery
	 * 3. Open system file picker via ScopedStorageHelper
	 * 4. Deliver selected file URIs to WebView callback
	 * 5. Clean up callback references
	 *
	 * ## Security Features:
	 * - Uses Android's scoped storage API for secure file access
	 * - Handles permission requests transparently
	 * - Clears callback references to prevent memory leaks
	 * - Supports multiple file selection
	 *
	 * @param webView The WebView initiating the file chooser
	 * @param filePathCallback Callback for delivering selected file URIs
	 * @param fileChooserParams Parameters describing the file chooser request
	 * @return True indicating the event was handled
	 */
	override fun onShowFileChooser(
		webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
		fileChooserParams: FileChooserParams?
	): Boolean {
		try {
			fileUploadCallback?.onReceiveValue(null)
			fileUploadCallback = filePathCallback

			val baseActivity = webView?.context as? BaseActivity ?: return false
			baseActivity.scopedStorageHelper?.openFilePicker(allowMultiple = true)
			baseActivity.scopedStorageHelper?.onFileSelected = { _, files ->
				val uris = files.map { it.uri }.toTypedArray()
				if (uris.isEmpty()) fileUploadCallback?.onReceiveValue(null)
				else fileUploadCallback?.onReceiveValue(uris)
				fileUploadCallback = null
			}
		} catch (error: Exception) {
			error.printStackTrace()
		}; return true
	}

	/**
	 * Handles full-screen custom view requests (typically HTML5 video).
	 *
	 * This method manages the transition to full-screen mode for embedded
	 * media content. It replaces the WebView with a custom view container
	 * and applies necessary transformations for proper full-screen display.
	 *
	 * ## Implementation Details:
	 * 1. Hides the WebView to prevent visual artifacts
	 * 2. Creates wrapper container for custom view
	 * 3. Applies landscape rotation for video content
	 * 4. Stores callback for later cleanup
	 *
	 * ## Visual Transformations:
	 * - Wraps content in FrameLayout for proper sizing
	 * - Forces landscape orientation for video
	 * - Applies rotation and translation transformations
	 * - Maintains aspect ratio during full-screen
	 *
	 * @param view The custom view (usually video surface) to display
	 * @param callback Callback for hiding the custom view
	 */
	override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
		// Hide the WebView when switching to fullscreen
		webviewEngine.currentWebView?.visibility = View.GONE

		val browserFragment = webviewEngine.browserFragment
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webViewContainer = browserFragmentBody.webViewContainer

		// Clear any existing views (important when switching multiple times)
		webViewContainer.removeAllViews()

		view?.let { video ->
			videoView = video

			// Wrap the video view in a FrameLayout so it fills the container
			val wrapper = FrameLayout(video.context).apply {
				layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
				addView(video, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
			}

			// Add the wrapper to the container
			webViewContainer.addView(wrapper)

			// Keep references for cleanup later
			customView = wrapper
			customViewCallback = callback

			// Adjust orientation and layout for fullscreen
			forceLandscapeRotation()
		}
	}

	/**
	 * Applies landscape rotation transformations to full-screen video view.
	 *
	 * This method ensures video content displays correctly in landscape
	 * orientation even on devices that are locked in portrait mode. It
	 * calculates proper dimensions and applies rotation/translation
	 * transformations to fill the available space.
	 *
	 * ## Transformation Details:
	 * - Swaps width and height dimensions
	 * - Rotates view 90 degrees clockwise
	 * - Adjusts translation to maintain viewport alignment
	 * - Uses post() for proper view dimension timing
	 */
	private fun forceLandscapeRotation() {
		val browserFragment = webviewEngine.browserFragment
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webViewContainer = browserFragmentBody.webViewContainer

		videoView?.let { view ->
			view.post {
				val containerWidth = webViewContainer.width
				val containerHeight = webViewContainer.height
				val params = view.layoutParams

				// Swap width/height for rotation
				params.width = containerHeight
				params.height = containerWidth
				view.layoutParams = params

				// Set pivot for rotation
				view.pivotX = 0f
				view.pivotY = 0f

				// Rotate 90 degrees for landscape
				view.rotation = 90f

				// Translate so the rotated view fills the container
				view.translationX = containerWidth.toFloat()
				view.translationY = 0f
			}
		}
	}

	/**
	 * Handles exit from full-screen custom view mode.
	 *
	 * This method restores normal browsing interface by:
	 * 1. Resetting all video view transformations
	 * 2. Removing custom view from container
	 * 3. Restoring the original WebView
	 * 4. Notifying the WebView system of the transition
	 *
	 * ## Cleanup Process:
	 * - Resets rotation and translation transformations
	 * - Clears view references to prevent memory leaks
	 * - Restores WebView visibility and layout
	 * - Invokes system callback for proper state management
	 */
	override fun onHideCustomView() {
		// Reset transformations for the video view
		videoView?.let { view ->
			view.rotation = 0f
			view.translationX = 0f
			view.translationY = 0f
			view.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
		}

		// Clear reference
		videoView = null

		val browserFragment = webviewEngine.browserFragment
		val browserFragmentBody = browserFragment.browserFragmentBody
		val webViewContainer = browserFragmentBody.webViewContainer

		// Remove fullscreen video and restore WebView
		webViewContainer.removeAllViews()
		webViewContainer.addView(webviewEngine.currentWebView)
		webviewEngine.currentWebView?.visibility = View.VISIBLE

		// Notify WebView that fullscreen has been closed
		customViewCallback?.onCustomViewHidden()
		customView = null
	}

	/**
	 * Persists cookies for the current URL to maintain session state.
	 *
	 * This method extracts cookies from the WebView's CookieManager and
	 * saves them to persistent storage. This ensures authentication and
	 * session data remains available for background operations such as
	 * file downloading or external API calls.
	 *
	 * ## Implementation Details:
	 * - Runs on IO dispatcher to avoid blocking UI thread
	 * - Extracts cookies via CookieManager API
	 * - Uses UserCookieStore for persistent storage
	 * - Handles null URL gracefully
	 *
	 * @param url The URL from which to extract cookies
	 */
	private fun persistCookies(url: String?, motherActivity: MotherActivity?) {
		motherActivity?.let { safeActivity ->
			ThreadsUtility.executeInBackground(timeOutInMilli = 1000, codeBlock = {
				url?.let {
					val cookies = CookieManager.getInstance().getCookie(it)
					UserCookieStore.saveCookie(it, cookies)
				}
			})
		}
	}
}