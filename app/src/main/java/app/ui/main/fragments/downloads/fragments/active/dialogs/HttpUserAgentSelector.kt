package app.ui.main.fragments.downloads.fragments.active.dialogs

import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import androidx.core.view.isVisible
import app.core.bases.BaseActivity
import app.core.engines.downloader.AIODownload
import app.ui.main.fragments.downloads.fragments.active.dialogs.HttpUserAgentSelector.Companion.MOBILE_HTTP_AGENT
import com.aio.R
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.WeakReference

/**
 * A dialog-based selector that allows users to choose or define a custom HTTP User-Agent
 * for a specific [AIODownload].
 *
 * This class manages a UI dialog with presets for common User-Agents (Mobile and Desktop)
 * and providing an input field for custom strings. Changes are applied directly to the
 * download's global settings and reported back via [onApplyListener].
 *
 * @property activity The [BaseActivity] context used to build the dialog and manage UI interactions.
 * @property downloadModel The data model representing the download for which the User-Agent is being configured.
 */
class HttpUserAgentSelector(val activity: BaseActivity, val downloadModel: AIODownload) {

	/**
	 * Logger instance used for logging diagnostic messages and events within this class.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the [BaseActivity] used to prevent memory leaks while
	 * providing access to the context for UI operations and dialog management.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(activity)

	/**
	 * Safely retrieves the [BaseActivity] from its [WeakReference].
	 *
	 * This property provides a way to access the activity context while preventing memory leaks.
	 * It may return `null` if the activity has been garbage collected or destroyed.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()

	/**
	 * Accessor for the global download settings associated with the current [downloadModel].
	 */
	private val downloadSettings get() = downloadModel.config

	companion object {
		/**
		 * A standard User-Agent string representing a mobile device (Android on Pixel 6 Pro).
		 * Used for HTTP requests to request the mobile version of web content.
		 */
		const val MOBILE_HTTP_AGENT =
			"Mozilla/5.0 (Linux; Android 12; Pixel 6 Pro) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36"

		/**
		 * The default User-Agent string used to emulate a desktop web browser (Chrome on Windows).
		 */
		const val DESKTOP_HTTP_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
	}

	/**
	 * Callback invoked when the user applies a selected HTTP User Agent.
	 *
	 * Provides the updated [AIODownload] as a parameter to the listener.
	 */
	var onApplyListener: (AIODownload) -> Unit? = {}

	/**
	 * Lazily initialized dialog builder for selecting the HTTP User Agent.
	 * Inflates the [R.layout.dialog_http_user_agent_1] layout to allow users to toggle
	 * between predefined mobile, desktop, or custom user agent strings.
	 */
	private val userAgentSelectorDialog by lazy {
		DialogBuilder(safeBaseActivity).apply { setView(R.layout.dialog_http_user_agent_1) }
	}

	/**
	 * Radio button for selecting the predefined mobile HTTP User Agent.
	 * Lazily initialized by finding the [R.id.btn_mobile_user_agent] view within the dialog layout.
	 */
	private val mobileAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_mobile_user_agent)
	}

	/**
	 * Radio button used to select the predefined desktop User-Agent string.
	 * This button is retrieved from the [userAgentSelectorDialog] layout.
	 */
	private val desktopAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_desktop_user_agent)
	}

	/**
	 * Radio button that allows the user to select and input a custom HTTP User-Agent string.
	 * When selected, it typically makes the [customAgentEditor] visible for manual entry.
	 */
	private val customAgentRadioBtn by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<RadioButton>(R.id.btn_custom_user_agent)
	}

	/**
	 * The container view for the custom User-Agent input field.
	 * This view is typically toggled visible or hidden based on whether the custom User-Agent
	 * option is selected, and serves as a wrapper for the [customAgentEditor].
	 */
	private val containerAgentEditField by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<View>(R.id.edit_user_agent_container)
	}

	/**
	 * An [EditText] field that allows the user to manually input or edit a custom HTTP User-Agent string.
	 * This field is typically visible only when the custom agent radio button is selected.
	 */
	private val customAgentEditor by lazy {
		val layout = userAgentSelectorDialog.view
		layout.findViewById<EditText>(R.id.edit_user_agent)
	}

	/**
	 * Temporarily holds the selected predefined User-Agent string (e.g., Mobile or Desktop)
	 * before it is committed to the download settings upon dialog confirmation.
	 */
	private var tempUserAgentHolder: String? = null

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
	 * Configures the initial state of the User Agent selector dialog based on the current
	 * download settings and displays the dialog to the user.
	 *
	 * This method checks the existing User Agent string to determine which radio button
	 * (Mobile, Desktop, or Custom) should be selected and toggles the visibility
	 * of the custom input field accordingly.
	 */
	fun show() {
		when (downloadSettings.downloadHttpUserAgent) {
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
				customAgentEditor.setText(downloadSettings.downloadHttpUserAgent)
			}
		}

		userAgentSelectorDialog.show()
	}

	/**
	 * Dismisses the user agent selector dialog.
	 */
	fun close() = userAgentSelectorDialog.close()

	/**
	 * Requests focus for the custom user agent input field, selects any existing text,
	 * and displays the on-screen soft keyboard.
	 */
	private fun focusInputField() {
		customAgentEditor?.focusable
		customAgentEditor?.selectAll()
		showOnScreenKeyboard(safeBaseActivity, customAgentEditor)
	}

	/**
	 * Updates the UI to reveal the custom User Agent input fields.
	 *
	 * This method is triggered when the custom agent radio button is selected,
	 * making the [containerAgentEditField] and [customAgentEditor] visible to the user.
	 */
	private fun selectCustomUserAgent() {
		containerAgentEditField.visibility = View.VISIBLE
		customAgentEditor.visibility = View.VISIBLE
	}

	/**
	 * Sets the temporary user agent to the predefined desktop browser string
	 * and hides the custom user agent input field.
	 */
	private fun selectDesktopUserAgent() {
		tempUserAgentHolder = DESKTOP_HTTP_AGENT
		customAgentEditor.visibility = View.GONE
	}

	/**
	 * Sets the temporary User Agent holder to the predefined [MOBILE_HTTP_AGENT] string
	 * and hides the custom agent input field.
	 */
	private fun selectMobileUserAgent() {
		tempUserAgentHolder = MOBILE_HTTP_AGENT
		customAgentEditor.visibility = View.GONE
	}

	/**
	 * Validates and applies the selected User Agent to the download model.
	 *
	 * If a custom User Agent is selected, it ensures the input field is not empty.
	 * Otherwise, it applies the preset User Agent (Mobile or Desktop) based on selection.
	 * Once successfully applied, it triggers the [onApplyListener] and closes the dialog.
	 */
	private fun applyUserAgent() {
		if (customAgentRadioBtn.isChecked &&
			customAgentEditor.isVisible
		) {

			if (customAgentEditor.text.toString().isEmpty()) {
				safeBaseActivity?.doSomeVibration(20)
				showToast(safeBaseActivity, R.string.title_must_enter_valid_user_agent)
				return
			}

			downloadSettings.downloadHttpUserAgent = customAgentEditor.text.toString()
			close()
			onApplyListener(downloadModel)
			return
		}

		downloadSettings.downloadHttpUserAgent = tempUserAgentHolder!!
		close()
		onApplyListener(downloadModel)
	}

}