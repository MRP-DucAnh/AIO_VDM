package app.ui.others.information

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import app.core.AIOApp.Companion.IS_PREMIUM_USER
import app.core.AIOApp.Companion.IS_ULTIMATE_VERSION_UNLOCKED
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOKeyStrings.DONT_PARSE_URL_ANYMORE
import app.core.bases.BaseActivity
import app.core.engines.video_parser.parsers.SupportedURLs.isSocialMediaUrl
import app.core.engines.video_parser.parsers.VideoThumbGrabber.startParsingVideoThumbUrl
import app.ui.main.MotherActivity
import app.ui.main.fragments.browser.webengine.SingleResolutionPrompter
import app.ui.main.fragments.downloads.intercepter.VideoIntentUrlInterceptor
import com.aio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lib.device.IntentUtility.getIntentDataURI
import lib.networks.URLUtility.isValidURL
import lib.networks.URLUtilityKT.fetchWebPageContent
import lib.networks.URLUtilityKT.getWebpageTitleOrDescription
import lib.process.LogHelperUtils
import lib.ui.ActivityAnimator.animActivityFade
import lib.ui.builders.ToastView.Companion.showToast
import lib.ui.builders.WaitingDialog

/**
 * An activity that intercepts and processes URLs shared from other applications.
 *
 * This activity acts as an entry point for `ACTION_SEND` and `ACTION_VIEW` intents,
 * typically triggered by a "Share" action from browsers, social media apps, or other
 * applications. It intelligently processes the incoming URL based on its type and the
 * user's premium status.
 *
 * ### Core Responsibilities:
 * - **Intercepting:** Captures shared URLs from external applications.
 * - **Validation:** Ensures the received data is a valid and non-empty URL.
 * - **Routing:** Differentiates between social media and generic URLs to determine the
 *   appropriate processing pipeline.
 * - **Metadata Extraction:** For supported social media URLs, it fetches metadata like
 *   the video title and thumbnail to provide a richer user experience.
 * - **User Interaction:** Prompts the user with a download dialog for social media
 *   content or forwards the URL to the in-app browser for generic links.
 *
 * ### Processing Flow:
 * 1. The activity receives an intent with a URL.
 * 2. It validates the URL. If invalid, it notifies the user and closes.
 * 3. It checks if the user has premium access. Non-premium users are redirected to the
 *    main activity with the URL.
 * 4. For premium users, it classifies the URL (e.g., social media vs. generic).
 * 5. **Social Media URL:** It initiates a background process to fetch the page title
 *    and thumbnail, then displays a `SingleResolutionPrompter` dialog for direct download.
 */
class IntentInterceptActivity : BaseActivity() {
	
	/**
	 * Logger for debugging and tracking the URL interception lifecycle.
	 *
	 * This instance provides structured logging to monitor the activity's state,
	 * track the flow of URL processing, and diagnose issues during development.
	 * It captures key events such as intent reception, URL validation, and parsing outcomes.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * A nullable, safe reference to the activity instance, retrieved from `weakSelfReference`.
	 *
	 * This property provides a direct, but potentially null, reference to the `IntentInterceptActivity`.
	 * It is used for operations that need access to the activity context, such as displaying dialogs or toasts.
	 * Using this getter avoids the boilerplate of calling `.get()` on the `WeakReference` and ensures that
	 * background tasks do not hold a strong reference, preventing memory leaks if the activity is destroyed
	 * while tasks are still running.
	 *
	 * Always perform a null-check (e.g., using `?.let`) before using this reference to handle cases
	 * where the activity has already been garbage-collected.
	 */
	private val safeIntentInterceptActivityRef get() = getActivity() as? IntentInterceptActivity
	
	/**
	 * A flag that indicates whether the background URL parsing has been aborted.
	 *
	 * This becomes `true` if the user cancels the "Analyzing URL" dialog. It serves as a signal
	 * for the background processing coroutine to terminate its work early, preventing it from
	 * showing a download prompt or performing further network operations after cancellation.
	 */
	@Volatile
	private var isParsingTitleFromUrlAborted = false
	
	/**
	 * Sets the layout for this activity and applies a fade-in animation.
	 *
	 * This method returns the resource ID for a transparent layout, allowing the activity
	 * to appear as an overlay while it processes a shared URL in the background. A fade-in
	 * animation is applied to ensure a smooth visual transition when the activity becomes visible,
	 * creating a seamless user experience.
	 *
	 * @return The layout resource ID for the transparent activity.
	 */
	override fun onRenderingLayout(): Int {
		logger.d("onRenderingLayout: Applying fade animation to activity.")
		animActivityFade(safeIntentInterceptActivityRef)
		return R.layout.activity_transparent_1
	}
	
	/**
	 * Handles the back button press event for the activity.
	 *
	 * This override ensures that when the user presses the back button, the activity
	 * is closed gracefully with a fade-out animation. It also sets a flag to
	 * abort any ongoing background URL parsing, preventing unnecessary processing.
	 */
	override fun onBackPressActivity() {
		logger.d("onBackPressActivity: User pressed back, closing with fade animation.")
		closeActivityWithFadeAnimation(true)
	}
	
	/**
	 * Core method that drives the URL interception and processing logic.
	 *
	 * This method is called after the activity's layout is rendered. It serves as the main entry
	 * point for handling the incoming intent's URL. It performs a series of checks and validations
	 * before routing the URL to the appropriate processing pipeline.
	 *
	 * ### Execution Flow:
	 * 1.  **Extract URL**: Retrieves the URL from the incoming `Intent`.
	 * 2.  **Validate URL**: Checks if the URL is valid and non-empty. If not, it shows a toast
	 *     message and closes the activity.
	 * 3.  **Check Premium Status**: Verifies if the user is a premium user. If not, the intent is
	 *     forwarded to the `MotherActivity` for standard handling.
	 * 4.  **Wait for App Initialization**: If the app's core systems are still initializing, it
	 *     displays a waiting dialog to prevent race conditions.
	 * 5.  **Initiate Parsing**: Once the app is ready, it calls `startParsingTheIntentURL` to
	 *     begin the detailed analysis and processing of the URL.
	 *
	 * This method handles both social media URLs (with advanced parsing) and generic URLs
	 * (with basic interception) by delegating to specialized functions.
	 */
	override fun onAfterLayoutRender() {
		// Handle premium user flow
		safeIntentInterceptActivityRef?.let { activityRef ->
			logger.d("onAfterLayoutRender: Starting intent processing.")
			
			// Retrieve the shared URL from the incoming intent
			val intentUrl = getIntentDataURI(activityRef)
			logger.d("onAfterLayoutRender: Retrieved intent URL = $intentUrl")
			
			// If no URL is provided, exit the activity gracefully
			if (intentUrl.isNullOrEmpty()) {
				logger.d("onAfterLayoutRender: No URL found in the intent.")
				onBackPressActivity()
				return
			}
			
			// Handle invalid URLs with user feedback
			if (!isValidURL(intentUrl)) {
				logger.d("onAfterLayoutRender: Invalid URL detected.")
				doSomeVibration()
				showToast(activityRef, msgId = R.string.title_invalid_url)
				onBackPressActivity()
				return
			}
			
			// If user is not premium or ultimate version is not unlocked, forward to the main activity
			if (!IS_PREMIUM_USER || !IS_ULTIMATE_VERSION_UNLOCKED) {
				logger.d("onAfterLayoutRender: Non-premium user detected. Forwarding to main activity.")
				forwardIntentToMotherActivity()
				return
			}
			
			// Wait for full app's download system to finish initialization before processing URLs
			activityCoroutineScope.launch(Dispatchers.IO) {
				val appLoadingDialog = WaitingDialog(
					loadingMessage = getString(R.string.title_wait_till_apps_loads_up),
					baseActivityInf = activityRef,
					isCancelable = false,
					shouldHideOkayButton = true
				)
				
				try {
					withTimeout(10_000) {
						// Wait loop until app's download system is fully initialized and loaded
						while (downloadSystem.isInitializing) {
							withContext(Dispatchers.Main) {
								appLoadingDialog.dialogBuilder?.isShowing?.let { isShowing ->
									if (!isShowing) appLoadingDialog.show()
								}
							}
							
							// Small delay to prevent tight loop
							delay(500)
						}
					}
					
					withContext(Dispatchers.Main) { appLoadingDialog.close() }
					startParsingTheIntentURL(intentUrl, activityRef)
				} catch (error: Exception) {
					logger.e("onAfterLayoutRender: Error occurred during URL processing", error)
					withContext(Dispatchers.Main) {
						appLoadingDialog.close()
						onBackPressActivity()
					}
				}
			}
			return
		}
	}
	
	/**
	 * Initiates the URL parsing pipeline for a given URL.
	 *
	 * This function serves as the central router for processing intercepted URLs. It first
	 * classifies the URL as either a social media link or a generic one.
	 *
	 * - **Social Media URLs:** An "Analyzing URL" dialog is shown while a background task
	 *   fetches the page's HTML content to extract the title and thumbnail. If successful,
	 *   a `SingleResolutionPrompter` is displayed, allowing the user to download the media
	 *   directly. If parsing fails or is canceled by the user, it gracefully falls back
	 *   to opening the URL in the in-app browser.
	 *
	 * - **Generic URLs:** The URL is passed to `interceptNonSocialMediaUrl` for standard
	 *   interception, which may lead to a direct download prompt or forwarding to the
	 *   in-app browser.
	 *
	 * This entire process is designed for premium users to provide an enhanced experience
	 * for supported media links.
	 *
	 * @param intentUrl The URL string received from the intent to be processed.
	 * @param activityRef A reference to the current `IntentInterceptActivity` instance,
	 *                    used for UI operations like showing dialogs and toasts.
	 */
	private suspend fun startParsingTheIntentURL(
		intentUrl: String,
		activityRef: IntentInterceptActivity
	) {
		logger.d("Premium user detected. Processing URL.")
		
		// Route to appropriate processor based on URL type
		if (!isSocialMediaUrl(intentUrl)) {
			logger.d("Non-social media URL detected. Using generic interceptor.")
			interceptNonSocialMediaUrl(activityRef, intentUrl)
			return
		}
		
		logger.d("Social media URL detected. Starting advanced parsing.")
		
		// Show "analyzing URL" waiting dialog for social media processing
		val analyzingUrlDialog = WaitingDialog(
			isCancelable = false,
			baseActivityInf = activityRef,
			loadingMessage = getString(R.string.title_analyzing_url_please_wait),
		)
		
		withContext(Dispatchers.Main) {
			// Setup waiting dialog "Okay" button click handler
			analyzingUrlDialog.dialogBuilder?.setOnClickForPositiveButton {
				logger.d("WaitingDialog: User cancelled analyzing process.")
				analyzingUrlDialog.close()
				isParsingTitleFromUrlAborted = true
				activityCoroutineScope.cancel("User cancelled parsing")
				closeActivityWithFadeAnimation(shouldAnimate = true)
			}
			
			analyzingUrlDialog.show()
			logger.d("WaitingDialog: Displayed analyzing message.")
		}
		
		try {
			withTimeout(20_000) {
				// Perform background parsing for social media URLs
				logger.d("Background Task: Fetching HTML content for URL.")
				val htmlBody = withContext(Dispatchers.IO) {
					fetchWebPageContent(intentUrl, retry = true, numOfRetry = 3)
				}
				
				if (isParsingTitleFromUrlAborted) return@withTimeout
				
				logger.d("Background Task: Parsing thumbnail URL.")
				val thumbnailUrl = withContext(Dispatchers.IO) {
					startParsingVideoThumbUrl(intentUrl, htmlBody)
				}
				
				if (isParsingTitleFromUrlAborted) return@withTimeout
				
				logger.d("Background Task: Extracting webpage title or description.")
				val resultedTitle = getWebpageTitleOrDescription(intentUrl, userGivenHtmlBody = htmlBody)
				if (isParsingTitleFromUrlAborted) return@withTimeout
				
				// Close the waiting dialog in UI thread
				logger.d("Background Task: Parsing completed. Closing waiting dialog.")
				withContext(Dispatchers.Main) { analyzingUrlDialog.close() }
				
				val isValidIntentUrl = !resultedTitle.isNullOrEmpty()
				logger.d(
					"Background Task: Parsing result: " +
						"validTitle=$isValidIntentUrl, cancelled=$isParsingTitleFromUrlAborted"
				)
				
				// Show download prompt if parsing was successful and not cancelled
				if (isValidIntentUrl && isParsingTitleFromUrlAborted == false) {
					logger.d("Background Task: Showing SingleResolutionPrompter dialog.")
					withContext(Dispatchers.Main) {
						val resolutionName = getText(R.string.title_high_quality).toString()
						SingleResolutionPrompter(
							baseActivity = activityRef,
							isDialogCancelable = true,
							singleResolutionName = resolutionName,
							extractedVideoLink = intentUrl,
							currentWebUrl = intentUrl,
							videoTitle = resultedTitle,
							videoUrlReferer = intentUrl,
							dontParseFBTitle = true,
							thumbnailUrlProvided = thumbnailUrl,
							isSocialMediaUrl = true,
							isDownloadFromBrowser = false,
							closeActivityOnSuccessfulDownload = true
						).show()
					}
				} else {
					// Fallback for busy server or cancelled execution
					logger.d("Background Task: Invalid result or cancelled. Forwarding to browser.")
					withContext(Dispatchers.Main) {
						activityRef.doSomeVibration()
						val stringResId = R.string.title_server_busy_opening_browser
						showToast(activityInf = activityRef, msgId = stringResId)
						forwardIntentToMotherActivity(dontParseURLAnymore = true)
					}
				}
			}
		} catch (error: Exception) {
			logger.e("Error occurred during URL parsing", error)
			withContext(Dispatchers.Main) {
				analyzingUrlDialog.close()
				forwardIntentToMotherActivity(dontParseURLAnymore = true)
			}
		}
	}
	
	/**
	 * Intercepts and processes non-social media URLs using a generic pipeline.
	 *
	 * This method is invoked for URLs that are not identified as social media links. It
	 * delegates the processing to the `SharedVideoURLIntercept` utility, which is designed
	 * to handle generic video URLs. This utility attempts to find downloadable media and,
	 * if successful, will close the current activity.
	 *
	 * If the interceptor cannot process the URL directly, it triggers a fallback mechanism
	 * (`onOpeningBuiltInBrowser`) which forwards the URL to the main activity's browser.
	 *
	 * @param activityRef A reference to the current `IntentInterceptActivity`, used as the
	 *                        context for the interceptor and for closing the activity upon completion.
	 * @param intentUrl The non-social media URL to be processed.
	 */
	private suspend fun interceptNonSocialMediaUrl(
		activityRef: IntentInterceptActivity,
		intentUrl: String
	) {
		withContext(Dispatchers.Main) {
			logger.d("interceptNonSocialMediaUrl: Starting interception for URL: $intentUrl")
			
			// Initialize the generic URL interceptor for non-social media links
			val videoIntentUrlInterceptor = VideoIntentUrlInterceptor(
				baseActivity = activityRef,
				closeActivityOnSuccessfulDownload = true,
				onOpeningBuiltInBrowser = {
					logger.d("interceptNonSocialMediaUrl: Opening browser as fallback.")
					forwardIntentToMotherActivity()
				}
			)
			
			// Process the given URL through the interceptor
			logger.d("interceptNonSocialMediaUrl: Passing URL to interceptor.")
			videoIntentUrlInterceptor
				.interceptIntentURI(intentUrl, shouldOpenBrowserAsFallback = true)
			logger.d("interceptNonSocialMediaUrl: Interception process initiated.")
		}
	}
	
	/**
	 * Forwards the intercepted intent to `MotherActivity` for standard handling.
	 *
	 * This function is invoked in scenarios where this `IntentInterceptActivity` cannot
	 * complete its specialized processing. It packages the original incoming intent
	 * (e.g., `ACTION_SEND` or `ACTION_VIEW`) into a new intent targeted at `MotherActivity`.
	 * This ensures the shared URL is still handled by the app's main interface, typically
	 * by opening it in the in-app browser.
	 *
	 * Common scenarios for forwarding include:
	 * - The user does not have premium access.
	 * - Advanced URL parsing for social media links fails or is canceled.
	 * - A fallback to the browser is explicitly required by another part of the app.
	 *
	 * The new intent preserves the original action, data, and extras. It also sets
	 * `FLAG_ACTIVITY_CLEAR_TOP` and `FLAG_ACTIVITY_SINGLE_TOP` to ensure `MotherActivity`
	 * is brought to the front without creating a new instance if one already exists.
	 *
	 * @param dontParseURLAnymore A boolean flag passed as an extra. If `true`, it signals
	 *   to `MotherActivity` that it should not re-attempt any special URL parsing and
	 *   should directly open the URL in the browser. This prevents redundant processing
	 *   loops.
	 */
	private fun forwardIntentToMotherActivity(dontParseURLAnymore: Boolean = false) {
		safeIntentInterceptActivityRef?.activityCoroutineScope?.launch(Dispatchers.IO) {
			logger.d("Preparing to forward intent. dontParseURLAnymore=$dontParseURLAnymore")
			
			try {
				val originalIntent = intent
				logger.d(
					"Retrieved original intent: " +
						"action=${originalIntent.action}, data=${originalIntent.data}"
				)
				
				// Create intent for MotherActivity with necessary flags and extras
				val targetIntent = Intent(getActivity(), MotherActivity::class.java).apply {
					action = originalIntent.action
					setDataAndType(originalIntent.data, originalIntent.type)
					putExtras(originalIntent)
					putExtra(DONT_PARSE_URL_ANYMORE, dontParseURLAnymore)
					flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
				}
				
				withContext(Dispatchers.Main) {
					// Launch MotherActivity with the processed intent
					logger.d("Launching MotherActivity with preserved intent.")
					startActivity(targetIntent)
					
					// Close current activity with fade animation
					closeActivityWithFadeAnimation(true)
					logger.d("Activity forwarded and closed with fade animation.")
				}
			} catch (error: Exception) {
				withContext(Dispatchers.Main) {
					logger.e("Error occurred while launching MotherActivity", error)
					
					// Fallback: open MotherActivity with default animation if forwarding fails
					openActivity(targetActivity = MotherActivity::class.java, shouldAnimate = true)
					closeActivityWithFadeAnimation(shouldAnimate = true)
					logger.d("Fallback - opened MotherActivity and closed current activity.")
				}
			}
		}
	}
}