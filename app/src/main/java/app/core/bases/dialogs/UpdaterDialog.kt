package app.core.bases.dialogs

import android.text.Html
import android.text.Html.FROM_HTML_MODE_COMPACT
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.core.engines.updater.AIOUpdater.UpdateInfo
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.device.ShareUtility.openApkFile
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.setViewOnClickListener
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.io.File
import java.lang.ref.WeakReference

/**
 * A dialog component responsible for displaying and handling application update notifications.
 *
 * This dialog presents users with information about a new application version available for update,
 * including version details and changelog. It provides an interface for users to initiate the
 * update installation process.
 *
 * The dialog is non-cancelable to ensure users explicitly acknowledge the update availability,
 * and it uses WeakReference to prevent memory leaks by avoiding strong references to the activity.
 *
 * @property baseActivity The BaseActivity context where the dialog will be displayed
 * @property latestVersionApkFile The File object representing the downloaded APK for the new version
 * @property versionInfo UpdateInfo object containing metadata about the available update
 */
class UpdaterDialog(
	private val baseActivity: BaseActivity,
	private val latestVersionApkFile: File,
	private val versionInfo: UpdateInfo
) {

	/**
	 * Logger instance for tracking dialog lifecycle events and debugging information.
	 * Used to log initialization, display, interaction, and closure events for debugging purposes.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the base activity to prevent memory leaks.
	 *
	 * Using WeakReference ensures that the dialog doesn't prevent the activity from being
	 * garbage collected when it's destroyed, while still allowing access to the activity
	 * while it's alive.
	 */
	private val weakReferenceOfActivity = WeakReference(baseActivity)

	/**
	 * Safe getter for the base activity reference that returns null if the activity has
	 * been garbage collected. This property provides null-safe access to the activity,
	 * preventing crashes when trying to access an activity that has been destroyed.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfActivity.get()

	/**
	 * DialogBuilder instance responsible for creating and managing the dialog UI.
	 *
	 * This builder configures the dialog's view, behavior, and lifecycle events,
	 * providing a standardized way to create and manage dialogs throughout the application.
	 */
	private val dialogBuilder = DialogBuilder(safeBaseActivityRef)

	/**
	 * Initializes the UpdaterDialog with the required configuration and UI setup.
	 *
	 * The initialization process includes:
	 * 1. Setting up the dialog view layout
	 * 2. Configuring dialog behavior (non-cancelable)
	 * 3. Setting up the changelog message display
	 * 4. Configuring button click listeners
	 *
	 * If the activity reference is null during initialization, the setup is skipped
	 * to prevent potential crashes.
	 */
	init {
		safeBaseActivityRef?.let { activityRef ->
			// Set the dialog layout from XML resources
			dialogBuilder.setView(R.layout.dialog_new_version_updater_1)

			// Prevent dialog dismissal by tapping outside or pressing back button
			dialogBuilder.setCancelable(false)

			// Setup the lottie animation component
			setupNewVersionUpdateAnimation()

			// Configure the changelog/update message display
			setupUpdateChangeLogMessage(activityRef)

			// Set up button click handlers
			setupClickListeners(activityRef)
		}
	}

	/**
	 * Displays the update dialog to the user if it's not already showing.
	 *
	 * This method checks if the dialog is currently visible before attempting to show it,
	 * preventing duplicate dialog instances. It should be called when a new version
	 * is available and ready for installation.
	 */
	fun show() {
		if (!dialogBuilder.isShowing) {
			dialogBuilder.show()
		}
	}

	/**
	 * Closes the update dialog if it's currently visible.
	 *
	 * This method safely dismisses the dialog, ensuring proper cleanup of resources.
	 * It can be called when the user interacts with the dialog or when the activity
	 * is being destroyed to prevent window leaks.
	 */
	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
		}
	}

	/**
	 * Sets up click listeners for interactive elements within the dialog.
	 *
	 * This method configures the positive action button (update button) to respond
	 * to user interactions. Other buttons in the layout can be added here as needed.
	 *
	 * @param activityRef The BaseActivity reference used for context-aware operations
	 */
	private fun setupClickListeners(activityRef: BaseActivity) {
		setViewOnClickListener(
			{ button: View -> this.setupClickEvents(button) },
			dialogBuilder.view, R.id.btn_dialog_positive_container
		)
	}

	/**
	 * Configures and starts any visual animations associated with the new version update dialog.
	 *
	 * This method is responsible for initializing animation sequences (such as Lottie animations,
	 * view transitions, or pulse effects) that draw the user's attention to the update availability
	 * or provide visual feedback during the update process.
	 *
	 * @param activityRef The BaseActivity reference used to contextually manage animation resources
	 */
	private fun setupNewVersionUpdateAnimation() {
		safeBaseActivityRef?.let { activityRef ->
			with(dialogBuilder.view) {
				findViewById<LottieAnimationView>(R.id.img_version_animation).apply {
					clipToCompositionBounds = false
					setScaleType(ImageView.ScaleType.FIT_XY)

					aioRawFiles.getNewVersionUpdateComposition()?.let {
						setComposition(it)
						playAnimation()
					} ?: setAnimation(R.raw.animation_new_app_version)

					showView(this, true, 100)
				}
			}
		}
	}

	/**
	 * Configures and displays the update changelog message in the dialog.
	 *
	 * This method:
	 * 1. Retrieves the latest version information from UpdateInfo
	 * 2. Formats it as HTML for rich text display
	 * 3. Sets up link movement method to handle any hyperlinks in the changelog
	 *
	 * The message typically includes version number and important update notes.
	 *
	 * @param activityRef The BaseActivity reference for string resource access
	 */
	private fun setupUpdateChangeLogMessage(activityRef: BaseActivity) {
		dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_message)?.let { textView ->
			// Format the version information as HTML for rich text display
			val htmlMsg = activityRef.getString(
				R.string.title_b_latest_version_b,
				versionInfo.latestVersion
			).trimIndent()

			// Parse HTML and enable link clicking if the changelog contains URLs
			textView.text = Html.fromHtml(htmlMsg, FROM_HTML_MODE_COMPACT)
			textView.movementMethod = LinkMovementMethod.getInstance()
		}
	}

	/**
	 * Handles click events for dialog buttons and performs appropriate actions.
	 *
	 * This method processes user interactions with dialog buttons and executes
	 * the corresponding business logic. Currently handles:
	 * - Positive button: Initiates the APK installation process
	 *
	 * Future button types can be added to the when statement as needed.
	 *
	 * @param button The View that was clicked, identified by its resource ID
	 */
	private fun setupClickEvents(button: View) {
		when (button.id) {
			R.id.btn_dialog_positive_container -> {
				// Close the dialog before proceeding with installation
				close()

				safeBaseActivityRef?.let { activityRef ->
					// Generate file provider authority for APK sharing
					val authority = "${activityRef.packageName}.provider"
					// Open the APK file using Android's package installer
					openApkFile(activityRef, latestVersionApkFile, authority)
				} ?: run {
					// Show error toast if activity reference is no longer valid
					showToast(safeBaseActivityRef, R.string.title_something_went_wrong)
				}
			}
			// Additional button handlers can be added here as needed
		}
	}
}