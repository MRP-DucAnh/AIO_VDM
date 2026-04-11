package app.ui.main.fragments.settings.activities.downloads.dialogs

import android.view.*
import android.widget.*
import androidx.core.view.*
import app.core.bases.*
import app.core.engines.settings.*
import com.aio.*
import lib.process.*
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.*

/**
 * Dialog for configuring global HTTP User-Agent settings for download operations.
 *
 * Allows users to select between predefined mobile/desktop user agents or input custom
 * agent strings. User-Agent configuration affects how servers identify client requests,
 * which can impact content delivery, compatibility, and feature availability during
 * download operations.
 *
 * The dialog provides three selection modes:
 * 1. Mobile Agent: Simulates Android Chrome on Pixel 6 Pro for mobile-optimized content
 * 2. Desktop Agent: Simulates Chrome on Windows 10 for desktop-optimized content
 * 3. Custom Agent: Allows arbitrary user agent strings for specialized requirements
 *
 * User agent settings are persisted globally through AIOSettings and affect all
 * subsequent download operations until changed. This configuration helps users
 * access content formatted for specific device types or bypass device restrictions.
 *
 * @property activity BaseActivity context for UI operations and dialog lifecycle management
 */
class GlobalDownloadUserAgent(activity: BaseActivity) {

	/**
	 * Logger instance for tracking dialog lifecycle events and user interactions.
	 *
	 * Records initialization, selection changes, validation errors, and apply operations
	 * to assist with debugging and user behavior analysis.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference wrapper for the base activity to prevent memory leaks.
	 *
	 * Safely holds activity reference without preventing garbage collection.
	 * Essential for dialogs that might outlive their parent activities.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(activity)

	/**
	 * Safe accessor for activity reference with null safety.
	 *
	 * Returns the activity if still available, null if garbage collected.
	 * All UI operations should check this property before proceeding.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfBaseActivity.get()

	/**
	 * Direct accessor for global application settings.
	 *
	 * Provides read/write access to user agent configuration through the
	 * injected AIOSettings instance. Acts as a convenience property.
	 */
	private val aioSettings get() = AIOSettingsRepo.getSettings()

	/**
	 * Companion object containing constant user agent definitions and shared values.
	 */
	companion object {
		/**
		 * Standard mobile user agent string simulating Android Chrome on Pixel 6 Pro.
		 *
		 * This agent typically triggers mobile-optimized content delivery from servers.
		 * Useful for downloading content formatted for mobile devices or accessing
		 * mobile-specific features/endpoints. Includes "Mobile" identifier in user agent.
		 */
		const val MOBILE_HTTP_AGENT =
			"Mozilla/5.0 (Linux; Android 12; Pixel 6 Pro) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36"

		/**
		 * Standard desktop user agent string simulating Chrome on Windows 10.
		 *
		 * This agent typically triggers desktop-optimized content delivery. Useful
		 * for accessing full desktop websites, bypassing mobile restrictions, or
		 * downloading content only available to desktop clients.
		 */
		const val DESKTOP_HTTP_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
	}

	/**
	 * Callback invoked when user successfully applies a new user agent configuration.
	 *
	 * Receives the updated AIOSettings instance containing the new user agent value.
	 * Typically used by parent components to refresh UI, update download operations,
	 * or trigger configuration sync across the application.
	 */
	var onApplyListener: (AIOSettings) -> Unit = {}

	/**
	 * Lazily initialized dialog builder for user agent selection interface.
	 *
	 * Inflates R.layout.dialog_http_user_agent_1 layout and manages dialog lifecycle.
	 * Laziness ensures resources are only allocated when dialog is actually needed,
	 * improving memory efficiency for infrequently used dialogs.
	 */
	private val userAgentSelectorDialog by lazy {
		DialogBuilder(safeBaseActivityRef)
			.apply { setView(R.layout.dialog_http_user_agent_1) }
	}

	/**
	 * Radio button for selecting mobile user agent preset.
	 *
	 * When selected, applies MOBILE_HTTP_AGENT constant. Lazily bound to
	 * R.id.btn_mobile_user_agent view in dialog layout.
	 */
	private val mobileAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_mobile_user_agent)
	}

	/**
	 * Radio button for selecting desktop user agent preset.
	 *
	 * When selected, applies DESKTOP_HTTP_AGENT constant. Lazily bound to
	 * R.id.btn_desktop_user_agent view in dialog layout.
	 */
	private val desktopAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_desktop_user_agent)
	}

	/**
	 * Radio button for enabling custom user agent input mode.
	 *
	 * When selected, reveals the custom agent input field and hides preset options.
	 * Lazily bound to R.id.btn_custom_user_agent view in dialog layout.
	 */
	private val customAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_custom_user_agent)
	}

	/**
	 * Container view wrapping the custom user agent input field.
	 *
	 * Provides visual grouping and toggleable visibility for custom input section.
	 * Becomes visible when custom agent mode is selected, providing clear visual
	 * distinction from preset options.
	 */
	private val containerAgentEditField by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<View>(R.id.edit_user_agent_container)
	}

	/**
	 * EditText field for manual user agent string entry.
	 *
	 * Enabled only in custom agent mode. Supports text selection, editing,
	 * and keyboard input for arbitrary user agent strings. Includes validation
	 * to prevent empty submissions.
	 */
	private val customAgentEditor by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<EditText>(R.id.edit_user_agent)
	}

	/**
	 * Temporary storage for selected preset user agent string.
	 *
	 * Holds mobile or desktop agent value when corresponding radio button is selected.
	 * Applied to settings only when user confirms selection via apply button.
	 * This temporary holder prevents premature settings modification.
	 */
	private var tempUserAgentHolder: String? = null

	/**
	 * Initializes the user agent selection dialog with click handlers and event bindings.
	 *
	 * Sets up comprehensive click mapping for all interactive elements using a
	 * declarative approach for clear handler organization. Mapped actions include:
	 * - Negative button (cancel/close)
	 * - Positive button (apply changes)
	 * - Radio button selections (mobile/desktop/custom)
	 * - Input field activation
	 */
	init {
		userAgentSelectorDialog.view.apply {
			val clickActions = mapOf(
				R.id.button_dialog_negative_container to ::close,
				R.id.btn_dialog_positive_container to ::applyUserAgent,
				R.id.btn_mobile_user_agent to ::selectMobileUserAgent,
				R.id.btn_desktop_user_agent to ::selectDesktopUserAgent,
				R.id.btn_custom_user_agent to ::selectCustomUserAgent,
				R.id.edit_user_agent_container to ::focusInputField
			)
			clickActions.forEach { (viewId, action) ->
				findViewById<View>(viewId).setOnClickListener { action() }
			}
		}
	}

	/**
	 * Displays the user agent selection dialog with current configuration pre-selected.
	 *
	 * Analyzes current aioSettings.downloadHttpUserAgent value to determine initial state:
	 * 1. Matches MOBILE_HTTP_AGENT: selects mobile radio button, hides custom field
	 * 2. Matches DESKTOP_HTTP_AGENT: selects desktop radio button, hides custom field
	 * 3. Other values: selects custom mode, populates field with current value
	 *
	 * Also manages visibility of custom input field based on selection mode to provide
	 * appropriate UI context for user interaction.
	 */
	fun show() {
		when (aioSettings.downloadHttpUserAgent) {
			MOBILE_HTTP_AGENT -> {
				mobileAgentRadioBtn.isChecked = true
				customAgentEditor.visibility = View.GONE
			}

			DESKTOP_HTTP_AGENT -> {
				desktopAgentRadioBtn.isChecked = true
				customAgentEditor.visibility = View.GONE
			}

			else -> {
				customAgentRadioBtn.isChecked = true
				containerAgentEditField.visibility = View.VISIBLE
				customAgentEditor.visibility = View.VISIBLE
				customAgentEditor.setText(aioSettings.downloadHttpUserAgent)
			}
		}

		userAgentSelectorDialog.show()
	}

	/**
	 * Closes and dismisses the user agent selection dialog.
	 *
	 * Performs cleanup of dialog resources and removes it from screen.
	 * Does not apply any pending changes - only closes the interface.
	 * Can be called programmatically or via user cancel action.
	 */
	fun close() = userAgentSelectorDialog.close()

	/**
	 * Activates the custom user agent input field for text entry.
	 *
	 * Performs three actions to facilitate user input:
	 * 1. Requests focus for the EditText field to accept keyboard input
	 * 2. Selects all existing text for easy replacement
	 * 3. Shows on-screen keyboard for text input convenience
	 *
	 * Typically called when user taps the custom input container to ensure
	 * immediate input readiness.
	 */
	private fun focusInputField() {
		customAgentEditor?.focusable
		customAgentEditor?.selectAll()
		showOnScreenKeyboard(safeBaseActivityRef, customAgentEditor)
	}

	/**
	 * Enables custom user agent input mode and reveals input field.
	 *
	 * Makes the custom agent container and editor visible when custom radio
	 * button is selected. Hides automatically when preset options are chosen.
	 * This dynamic UI adjustment provides context-appropriate interface.
	 */
	private fun selectCustomUserAgent() {
		containerAgentEditField.visibility = View.VISIBLE
		customAgentEditor.visibility = View.VISIBLE
	}

	/**
	 * Selects desktop user agent preset and prepares it for application.
	 *
	 * Sets tempUserAgentHolder to DESKTOP_HTTP_AGENT and hides custom input
	 * field since preset mode doesn't require manual entry. This clears
	 * any previous custom input from the interface.
	 */
	private fun selectDesktopUserAgent() {
		tempUserAgentHolder = DESKTOP_HTTP_AGENT
		customAgentEditor.visibility = View.GONE
	}

	/**
	 * Selects mobile user agent preset and prepares it for application.
	 *
	 * Sets tempUserAgentHolder to MOBILE_HTTP_AGENT and hides custom input
	 * field since preset mode doesn't require manual entry. This clears
	 * any previous custom input from the interface.
	 */
	private fun selectMobileUserAgent() {
		tempUserAgentHolder = MOBILE_HTTP_AGENT
		customAgentEditor.visibility = View.GONE
	}

	/**
	 * Validates and applies the selected user agent to application settings.
	 *
	 * Handles two application paths with appropriate validation:
	 * 1. Custom mode: Validates input isn't empty before applying
	 * 2. Preset mode: Applies tempUserAgentHolder value directly
	 *
	 * Validation failure in custom mode:
	 * - Triggers haptic feedback (20ms vibration)
	 * - Shows validation toast message
	 * - Returns without applying changes
	 *
	 * Success flow for both modes:
	 * - Updates aioSettings.downloadHttpUserAgent
	 * - Closes dialog via close()
	 * - Invokes onApplyListener callback with updated config
	 *
	 * Uses safe navigation (!!) for tempUserAgentHolder assuming proper
	 * initialization through radio button selections.
	 */
	private fun applyUserAgent() {
		// Handle custom user agent mode with validation
		if (customAgentRadioBtn.isChecked &&
			customAgentEditor.isVisible
		) {
			// Validate custom input isn't empty
			if (customAgentEditor.text.toString().isEmpty()) {
				safeBaseActivityRef?.doSomeVibration(20)
				showToast(safeBaseActivityRef, R.string.title_must_enter_valid_user_agent)
				return
			}

			// Apply custom user agent
			aioSettings.downloadHttpUserAgent = customAgentEditor.text.toString()
			close()
			onApplyListener(aioSettings)
			return
		}

		// Handle preset user agent mode (mobile or desktop)
		aioSettings.downloadHttpUserAgent = tempUserAgentHolder!!
		close()
		onApplyListener(aioSettings)
	}
}