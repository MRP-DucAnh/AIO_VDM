package app.core.bases.dialogs

import android.text.Html
import android.text.Html.FROM_HTML_MODE_COMPACT
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import app.core.bases.BaseActivity
import app.core.engines.updater.AIOUpdater.UpdateInfo
import com.aio.R
import lib.device.ShareUtility.openApkFile
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.setViewOnClickListener
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.io.File
import java.lang.ref.WeakReference

/**
 * Displays a user-friendly dialog prompting the user to install a new version of the application.
 *
 * This dialog serves as the primary user interface for the app update flow, presenting update
 * information in a clear, actionable manner while ensuring safe installation through proper
 * Android security protocols. The dialog is designed to be informative yet non-intrusive,
 * allowing users to make informed decisions about updating their application.
 *
 * Key features:
 * - Displays latest available version with clear version comparison
 * - Shows currently installed version for context (when available)
 * - Provides clickable changelog links for detailed update information
 * - One-tap installation process with proper APK handling
 * - Memory-safe implementation using weak activity references
 * - Prevents duplicate dialog instances and manages lifecycle properly
 *
 * The dialog uses a DialogBuilder for consistent UI theming and follows Material Design
 * principles for intuitive user interaction. It handles the complete update presentation
 * flow from information display to installation initiation.
 *
 * @property baseActivity The activity that hosts and displays the dialog, stored as a
 *                        weak reference to prevent memory leaks during configuration changes
 *                        or unexpected activity destruction.
 * @property latestVersionApkFile The downloaded APK file containing the new application
 *                                version, ready for installation verification and handling.
 * @property versionInfo Metadata container for the update including version string,
 *                       changelog URL, release notes, and other relevant update information.
 */
class UpdaterDialog(private val weakReferenceOfActivity: WeakReference<BaseActivity>?,
	private val latestVersionApkFile: File, private val versionInfo: UpdateInfo) {

	/**
	 * Logger instance for tracking dialog lifecycle events, user interactions,
	 * and potential errors during the update presentation process.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Memory-safe weak reference to the parent activity ensuring the dialog doesn't
	 * prevent garbage collection if the activity is destroyed while the dialog exists.
	 * This prevents common memory leak scenarios in Android dialog management.
	 */
	private val safeBaseActivityRef: BaseActivity?
		get() = weakReferenceOfActivity?.get()

	/**
	 * Dialog builder instance responsible for creating, configuring, and managing
	 * the dialog's visual presentation and interactive behavior throughout its lifecycle.
	 */
	private val dialogBuilder: DialogBuilder = DialogBuilder(safeBaseActivityRef)

	/**
	 * Initialization block that sets up the dialog's visual structure, content population,
	 * and interactive behavior. This runs immediately when the UpdaterDialog instance is
	 * created, preparing the dialog for display while ensuring safe activity reference usage.
	 */
	init {
		safeBaseActivityRef?.let { activity ->
			logger.d("Initializing UpdaterDialog with version ${versionInfo.latestVersion}")

			// Configure dialog visual structure using predefined layout resource
			dialogBuilder.setView(R.layout.dialog_new_version_updater_1)
			dialogBuilder.setCancelable(false) // Prevent accidental dismissal during critical update flow

			// Populate and format the version information message with HTML styling
			dialogBuilder.view.apply {
				findViewById<TextView>(R.id.txt_dialog_message)?.let { textView ->
					val htmlMsg = activity.getString(
						/* resId = */ R.string.title_b_latest_version_b,
						/* ...formatArgs = */ versionInfo.latestVersion
					).trimIndent()

					// Convert HTML-formatted string to styled text with clickable links
					textView.text = Html.fromHtml(htmlMsg, FROM_HTML_MODE_COMPACT)
					textView.movementMethod = LinkMovementMethod.getInstance() // Enable link clicking
				}
			}

			// Attach click handler to the primary action button for update installation
			setViewOnClickListener(
				{ button: View -> this.setupClickEvents(button) },
				dialogBuilder.view,
				R.id.btn_dialog_positive_container
			)
		} ?: logger.d("UpdaterDialog initialization skipped — " +
						"activity reference is null, dialog cannot be displayed")
	}

	/**
	 * Handles user interactions with dialog buttons and orchestrates the appropriate responses.
	 *
	 * This method processes click events from all interactive elements within the dialog,
	 * routing each to the corresponding action handler. Currently supports the primary
	 * installation action with extensibility for additional buttons like cancel or later options.
	 *
	 * @param button The clicked button view that triggered the event, used to determine
	 *               which action to execute based on its resource ID.
	 */
	private fun setupClickEvents(button: View) {
		logger.d("Dialog button clicked with id=${button.id}")
		when (button.id) {
			R.id.btn_dialog_positive_container -> {
				logger.d("Install button clicked — initiating update installation process")
				close() // Dismiss dialog before proceeding to installation
				safeBaseActivityRef?.let { activity ->
					// Use file provider authority for secure APK file sharing
					val authority = "${activity.packageName}.provider"
					openApkFile(activity, latestVersionApkFile, authority)
				} ?: showToast(safeBaseActivityRef, msgId = R.string.title_something_went_wrong)
			}
		}
	}

	/**
	 * Displays the update dialog to the user if it is not already visible.
	 *
	 * This method ensures the dialog is only shown once, preventing duplicate dialogs
	 * that could confuse users or cause interface conflicts. It performs a visibility
	 * check before attempting to display, maintaining clean dialog state management.
	 */
	fun show() {
		if (!dialogBuilder.isShowing) {
			logger.d("Displaying UpdaterDialog to user")
			dialogBuilder.show()
		} else {
			logger.d("UpdaterDialog already visible — skipping show() to prevent duplicates")
		}
	}

	/**
	 * Dismisses the update dialog if it is currently visible to the user.
	 *
	 * This method safely closes the dialog and releases associated resources.
	 * It checks the dialog's visibility state before attempting dismissal to
	 * avoid unnecessary operations or potential window manager exceptions.
	 */
	fun close() {
		if (dialogBuilder.isShowing) {
			logger.d("Closing UpdaterDialog and releasing resources")
			dialogBuilder.close()
		} else {
			logger.d("UpdaterDialog already closed — skipping redundant close operation")
		}
	}
}