package app.core.engines.video_parser.dialogs

import android.view.View
import android.widget.EditText
import app.core.bases.BaseActivity
import app.core.engines.video_parser.parsers.SupportedURLs.isSocialMediaUrl
import app.core.engines.video_parser.parsers.VideoThumbGrabber.startParsingVideoThumbUrl
import app.ui.main.MotherActivity
import app.ui.main.fragments.browser.webengine.SingleResolutionPrompter
import app.ui.main.fragments.downloads.intercepter.VideoIntentUrlInterceptor
import com.aio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.networks.URLUtility.isValidURL
import lib.networks.URLUtilityKT.fetchWebPageContent
import lib.networks.URLUtilityKT.getWebpageTitleOrDescription
import lib.process.AsyncJobUtils.executeOnMainThread
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import lib.ui.builders.WaitingDialog
import java.lang.ref.WeakReference

/**
 * A dialog that allows users to manually paste and process video URLs.
 *
 * This class provides a user interface for entering a URL and handles different link types:
 * - **Direct video URLs:** Passed directly to the download interceptor for immediate processing.
 * - **Social media URLs:** For supported sites, it attempts to extract the video's title and
 *   thumbnail before prompting the user with download options.
 * - **Other web pages:** If a URL is neither a direct video link nor a supported social media page,
 *   it falls back to opening the link in the in-app browser for further user action.
 *
 * The dialog can be pre-filled with a URL and can be configured to start the parsing process
 * automatically upon being displayed, streamlining the user workflow.
 *
 * @property baseActivity The [BaseActivity] context used for creating dialogs and accessing
 *   application-level components.
 * @property passOnUrl An optional URL to pre-populate in the input field. If provided, the user
 *   does not need to manually paste the link.
 * @property autoStart If `true` and `passOnUrl` is not null, URL processing will start
 *   immediately when the dialog is shown, bypassing user interaction. Defaults to `false`.
 */
class VideoLinkPasteEditor(
	val baseActivity: BaseActivity,
	val passOnUrl: String? = null,
	val autoStart: Boolean = false,
	val onDownloadFailed: (String) -> Unit = {}
) {
	
	/**
	 * Logger for this class, used for debugging and tracking the dialog's lifecycle and events.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * A weak reference to the hosting activity to prevent memory leaks.
	 *
	 * This allows the garbage collector to reclaim the activity's memory if the dialog
	 * outlives the activity, for example, during a configuration change.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)
	
	/**
	 * Provides a safe, nullable reference to the `BaseActivity`.
	 *
	 * This accessor retrieves the activity from the `weakReferenceOfBaseActivity`.
	 * Using a `WeakReference` is crucial to prevent memory leaks by allowing the
	 * Android framework to garbage-collect the activity if it's destroyed (e.g.,
	 * due to a configuration change or the user navigating away) while this
	 * dialog class might still be held in memory by a background thread.
	 *
	 * Always check for `null` before using this property to ensure the activity is still available.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()
	
	/**
	 * Manages the underlying dialog instance.
	 *
	 * This property holds the [DialogBuilder] which is responsible for creating, displaying, and
	 * managing the dialog's lifecycle. It is initialized with a reference to the activity
	 * and can be set to `null` to release resources when the dialog is no longer needed,
	 * for instance, upon dismissal. Making it nullable helps prevent memory leaks.
	 */
	private var dialogBuilder: DialogBuilder? = DialogBuilder(safeBaseActivity)
	
	/**
	 * The "Download" button in the dialog that initiates the video download process.
	 *
	 * This button serves as the primary action trigger within the dialog interface.
	 * When tapped, it validates the URL entered by the user, performs necessary format
	 * checks, and launches the video title parsing workflow. The button's state may be
	 * disabled during processing to prevent duplicate requests and provide visual feedback
	 * to the user about ongoing operations. It is initialized during dialog setup and
	 * retains click handling logic throughout the dialog's lifecycle.
	 */
	private lateinit var buttonDownload: View
	
	/**
	 * The input field where the user pastes or types the video URL.
	 *
	 * This [EditText] component serves as the user's primary interface for providing
	 * video source URLs. It supports text input, paste operations, and may include
	 * validation hints or placeholder text to guide the user. The field is configured
	 * to accept various URL formats and may include input filters to prevent invalid
	 * characters. Text entered here is captured and processed when the download button
	 * is activated, forming the basis for subsequent network requests and video parsing.
	 */
	private lateinit var editFieldFileURL: EditText
	
	/**
	 * The container view for the URL input field. This view often includes the [EditText]
	 * itself and might have a background or border. Clicking this container will focus
	 * the input field, making it easier for the user to start typing or pasting a URL.
	 *
	 * This container provides visual structure and additional interaction affordances
	 * for the URL input field. It may include padding, background styling, or borders
	 * to distinguish it from other UI elements. The click-to-focus behavior enhances
	 * usability by allowing users to activate the input field by tapping anywhere within
	 * this container, not just the text area. It is typically initialized alongside
	 * `editFieldFileURL` during dialog layout inflation.
	 */
	private lateinit var editFieldContainer: View
	
	/**
	 * Stores the URL provided by the user in the dialog's input field.
	 * This property is populated when the user clicks the download button,
	 * capturing the text from `editFieldFileURL` for validation and processing.
	 *
	 * This variable serves as a temporary holding place for the user's URL input
	 * after the download action is initiated. It contains the raw string extracted
	 * from the input field before any processing or validation occurs. The value
	 * is used throughout the download pipeline, including URL format validation,
	 * network requests for metadata extraction, and eventual video download
	 * operations. It is cleared or updated with each download attempt.
	 */
	private var userGivenURL: String = ""
	
	/**
	 * A flag to indicate whether the user has canceled the process of parsing the video title
	 * from a URL. This is set to `true` when the user dismisses the "analyzing" waiting dialog,
	 * preventing subsequent actions (like showing the download prompter) from executing.
	 *
	 * This boolean flag tracks user-initiated cancellation during the intermediate
	 * processing stage between URL submission and title extraction. When set to `true`,
	 * it signals that the user has manually aborted the operation by dismissing any
	 * progress or loading dialogs, and all subsequent processing steps should be
	 * terminated. This prevents race conditions where background parsing might
	 * complete after the user has already indicated they want to stop the operation.
	 * The flag should be reset to `false` when starting a new parsing operation.
	 */
	private var isParsingTitleFromUrlAborted = false
	
	/**
	 * Initializes the dialog, inflates its layout, and sets up click listeners.
	 *
	 * This initialization block is executed when the dialog instance is created.
	 * It performs essential setup tasks including layout inflation, view binding,
	 * and click listener configuration. The method first validates that the hosting
	 * activity is still available through a safe reference, then proceeds to
	 * initialize the dialog UI components. If the activity reference is null,
	 * initialization is aborted to prevent runtime crashes. The method also handles
	 * optional pre-filling of the URL field when an initial URL is provided.
	 */
	init {
		// Safely access the base activity to ensure it's still available
		safeBaseActivity?.let { activityRef ->
			// Proceed with dialog builder initialization if available
			dialogBuilder?.let { builder ->
				// Inflate the dialog layout from XML resource
				builder.setView(R.layout.dialog_video_link_editor_1)
				builder.view.apply {
					// Bind UI components from the inflated layout to properties
					buttonDownload = findViewById(R.id.btn_dialog_positive_container)
					editFieldContainer = findViewById(R.id.edit_url_container)
					editFieldFileURL = findViewById(R.id.edit_file_url)
					
					// Pre-fill URL field if an initial URL was provided
					passOnUrl?.let { url ->
						editFieldFileURL.setText(url)
						logger.d("Pre-filled URL in editor: $url")
					}
					
					// Define click actions for interactive UI elements
					val clickActions = mapOf(
						editFieldContainer to { focusEditTextField() },
						buttonDownload to { downloadVideo() }
					)
					
					// Apply click listeners to all configured views
					clickActions.forEach { (view, action) -> view.setOnClickListener { action() } }
					logger.d("Dialog initialized with click listeners.")
				}
			}
		} ?: logger.d("Dialog initialization failed: activity reference is null.")
	}
	
	/**
	 * Displays the dialog and focuses input. If [autoStart] and [passOnUrl] are provided,
	 * begins parsing immediately.
	 *
	 * This method controls the visibility of the dialog interface. It supports two modes:
	 * - Auto-start mode: When both `autoStart` is true and `passOnUrl` contains a URL,
	 *   the method skips showing the dialog UI and immediately begins the download process.
	 * - Manual mode: Shows the dialog to the user, focuses the input field, and displays
	 *   the on-screen keyboard for immediate interaction.
	 *
	 * The delayed focus and keyboard display ensures the dialog is fully rendered before
	 * attempting UI modifications, preventing visual glitches.
	 */
	fun show() {
		// Check if auto-start mode is enabled with a pre-filled URL
		if (!passOnUrl.isNullOrEmpty() && autoStart) {
			// Skip UI and immediately start download process
			logger.d("Auto-start enabled. Starting download for pre-filled URL: $passOnUrl")
			downloadVideo()
		} else {
			// Show the dialog to the user
			dialogBuilder?.show()
			logger.d("Dialog shown to user.")
			
			// Delay UI adjustments to ensure dialog is fully rendered
			delay(200, object : OnTaskFinishListener {
				override fun afterDelay() {
					// Focus the URL input field and select all text
					focusEditTextField()
					editFieldFileURL.selectAll()
					
					// Display on-screen keyboard for immediate text input
					showOnScreenKeyboard(safeBaseActivity, editFieldFileURL)
					logger.d("Input field focused and keyboard shown.")
				}
			})
		}
	}
	
	/**
	 * Closes the dialog if open.
	 *
	 * This method safely dismisses the dialog interface and cleans up associated resources.
	 * It checks if the dialog builder instance exists and is currently showing before
	 * attempting to close it, preventing potential null pointer exceptions. Logging is
	 * included to track dialog lifecycle events for debugging purposes.
	 */
	fun close() {
		logger.d("Closing dialog.")
		dialogBuilder?.close()
	}
	
	/**
	 * Requests focus on the input field.
	 *
	 * This helper method programmatically sets focus to the URL input EditText field.
	 * It is typically called after the dialog is displayed to ensure the user can
	 * immediately begin typing or pasting a URL without needing to manually tap the
	 * input field. The method logs focus requests for debugging user interaction flow.
	 */
	private fun focusEditTextField() {
		logger.d("Focusing input field.")
		editFieldFileURL.requestFocus()
	}
	
	/**
	 * Initiates the video processing workflow based on the URL provided by the user.
	 *
	 * This function is triggered when the user taps the "Download" button. It first retrieves the
	 * URL from the `editFieldFileURL` input field and validates its format.
	 *
	 * - If the URL is invalid, it displays an error toast and aborts.
	 * - If the URL is a supported social media link (checked via `isSocialMediaUrl`), it closes
	 *   the dialog and starts an asynchronous process to fetch the webpage's title and a thumbnail.
	 *   Upon success, it presents a `SingleResolutionPrompter` to the user. If title extraction fails,
	 *   it falls back to opening the URL in the in-app browser.
	 * - If the URL is not a social media link, it's treated as a direct video link and is passed
	 *   to `startParingVideoURL` for immediate download interception.
	 *
	 * During social media URL analysis, a waiting dialog is shown. The user can cancel this
	 * operation, which sets the `isParsingTitleFromUrlAborted` flag to prevent further actions.
	 */
	private fun downloadVideo() {
		safeBaseActivity?.let { activityRef ->
			userGivenURL = editFieldFileURL.text.toString()
			logger.d("Download button clicked with URL: $userGivenURL")
			
			if (!isValidURL(userGivenURL)) {
				logger.d("Invalid URL entered: $userGivenURL")
				activityRef.doSomeVibration()
				showToast(activityRef, R.string.title_file_url_not_valid)
				return
			} else {
				logger.d("Valid URL detected. Closing dialog and processing.")
				close()
				
				if (isSocialMediaUrl(userGivenURL)) {
					logger.d("URL identified as social media link. Starting analysis.")
					val waitingDialog = WaitingDialog(
						isCancelable = false,
						baseActivityInf = baseActivity,
						loadingMessage = getText(R.string.title_analyzing_url_please_wait),
						dialogCancelListener = { dialog ->
							isParsingTitleFromUrlAborted = true
							logger.d("Parsing aborted by user.")
							dialog.dismiss()
						}
					)
					
					waitingDialog.show()
					logger.d("Waiting dialog shown.")
					activityRef.activityCoroutineScope.launch(Dispatchers.IO) {
						val htmlBody = fetchWebPageContent(userGivenURL, retry = true)
						logger.d("Fetched HTML content for URL: $userGivenURL")
						
						val thumbnailUrl = startParsingVideoThumbUrl(userGivenURL, htmlBody)
						logger.d("Parsed thumbnail URL: ${thumbnailUrl ?: "none"}")
						
						getWebpageTitleOrDescription(
							websiteUrl = userGivenURL,
							returnDescription = false,
							userGivenHtmlBody = htmlBody,
							callback = { resultedTitle ->
								waitingDialog.close()
								if (!resultedTitle.isNullOrEmpty() && !isParsingTitleFromUrlAborted) {
									logger.d("Extracted title: $resultedTitle")
									executeOnMainThread {
										SingleResolutionPrompter(
											baseActivity = baseActivity,
											singleResolutionName = getText(R.string.title_high_quality),
											extractedVideoLink = userGivenURL,
											currentWebUrl = userGivenURL,
											videoTitle = resultedTitle,
											videoUrlReferer = userGivenURL,
											isSocialMediaUrl = true,
											isDownloadFromBrowser = false,
											dontParseFBTitle = true,
											thumbnailUrlProvided = thumbnailUrl
										).show()
									}
								} else {
									logger.d("Failed to extract title. Opening browser fallback.")
									executeOnMainThread {
										if (activityRef is MotherActivity) {
											activityRef.doSomeVibration()
											showToast(activityRef, R.string.title_server_busy_opening_browser)
											activityRef.browserFragment?.getBrowserWebEngine()?.let {
												activityRef.sideNavigation?.addNewBrowsingTab(userGivenURL, it)
												activityRef.openBrowserFragment()
											}
										}
										onDownloadFailed.invoke("Failed to extract title.")
									}
								}
							}
						)
					}
				} else {
					logger.d("Direct video URL detected. Starting interception.")
					startParingVideoURL(activityRef)
				}
			}
		} ?: logger.d("downloadVideo() invoked with null activity reference.")
	}
	
	/**
	 * Triggers download interception for direct video URLs or other non-social media links.
	 *
	 * This method is called when the provided URL is not identified as a supported social media link.
	 * It passes the URL to the [VideoIntentUrlInterceptor] class, which handles the process of
	 * sniffing for video content and prompting the user to download it.
	 *
	 * @param safeActivity The active [MotherActivity] instance, required for context.
	 */
	private fun startParingVideoURL(activityRef: BaseActivity) {
		logger.d("Intercepting direct video URL: $userGivenURL")
		close()
		val videoInterceptor = VideoIntentUrlInterceptor(activityRef)
		videoInterceptor.interceptIntentURI(userGivenURL)
	}
}