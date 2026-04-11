package app.ui.main.fragments.settings.activities.browser.dialogs

import android.view.*
import android.widget.*
import app.core.bases.*
import app.core.engines.settings.*
import app.ui.main.fragments.settings.activities.browser.dialogs.WebHttpUserAgentSelector.Companion.DESKTOP_HTTP_AGENT
import app.ui.main.fragments.settings.activities.browser.dialogs.WebHttpUserAgentSelector.Companion.MOBILE_HTTP_AGENT
import com.aio.*
import kotlinx.coroutines.*
import lib.process.*
import lib.ui.ViewUtility.showOnScreenKeyboard
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.*

/**
 * A dialog-based configuration utility for managing the browser's HTTP User Agent identity.
 *
 * This class encapsulates the logic for displaying a selection interface that allows
 * users to switch between standard mobile and desktop identities or define a custom
 * identifier. It manages UI state transitions, input validation, and thread-safe
 * interaction with the application settings.
 *
 * Architectural Design:
 * * **Memory Safety**: Uses a [WeakReference] to the [BaseActivity] to prevent context
 * leaks during asynchronous operations or configuration changes.
 * * **Main-Safety**: All internal logic is marked as [suspend] and utilizes
 * [withMainContext] to ensure UI manipulations occur on the correct thread.
 * * **Reactive UI**: Implements a functional [onApplyListener] to propagate setting
 * changes back to the parent activity or browser engine immediately.
 *
 * @property activity The parent [BaseActivity] providing the context for layout
 * inflation and coroutine management.
 * @property aioSetting The [AIOSettings] instance used to track and persist
 * User Agent preferences.
 */
class WebHttpUserAgentSelector(val activity: BaseActivity, val aioSetting: AIOSettings) {

	/**
	 * Utility for logging diagnostic messages and errors specifically for the
	 * User Agent selector, using the class name as the identifying tag.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to the parent [BaseActivity].
	 *
	 * This ensures that the dialog logic does not prevent the Activity from being
	 * garbage collected during configuration changes or when the Activity is finished.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(activity)

	/**
	 * Provides null-safe access to the underlying Activity.
	 *
	 * If the [weakReferenceOfBaseActivity] has been cleared, this returns null,
	 * preventing crashes during asynchronous operations that might complete
	 * after the UI context is gone.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()

	/**
	 * Dynamic access to the centralized [AIOSettings] via the application's
	 * repository pattern.
	 *
	 * This property ensures the dialog is always interacting with the most
	 * up-to-date configuration state stored in the ObjectBox database.
	 */
	private val aIOSettings get() = AIOSettingsRepo.getSettings()

	/**
	 * Static constants representing standard User Agent strings used to identify
	 * the browser engine to web servers.
	 */
	companion object {
		/**
		 * A standard Chrome-based User Agent string mimicking a modern Android 12 device.
		 * Used to request mobile-optimized versions of websites.
		 */
		const val MOBILE_HTTP_AGENT =
			"Mozilla/5.0 (Linux; Android 12; Pixel 6 Pro) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/94.0.4606.61 Mobile Safari/537.36"

		/**
		 * A standard Chrome-based User Agent string mimicking a Windows 10 desktop environment.
		 * Used to bypass mobile redirects and request full desktop site versions.
		 */
		const val DESKTOP_HTTP_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
				"(KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
	}

	/**
	 * A functional callback invoked when a new User Agent has been successfully
	 * selected and applied within the dialog.
	 *
	 * This listener provides the updated [AIOSettings] instance to the caller,
	 * allowing for immediate synchronization of the browser engine or UI components.
	 */
	var onApplyListener: (AIOSettings) -> Unit? = {}

	/**
	 * Lazily initialized [DialogBuilder] that serves as the root container for the
	 * User Agent selection interface.
	 *
	 * By using 'lazy', the layout inflation of [R.layout.dialog_http_user_agent_1]
	 * is deferred until the first time [show] is called, optimizing initial
	 * class instantiation memory usage.
	 */
	private val userAgentSelectorDialog by lazy {
		DialogBuilder(safeBaseActivity).apply {
			setView(R.layout.dialog_http_user_agent_1)
		}
	}

	/**
	 * Radio button facilitating the selection of the predefined Mobile
	 * User Agent string.
	 */
	private val mobileAgentRadioBtn by lazy {
		userAgentSelectorDialog.view
			.findViewById<RadioButton>(R.id.btn_mobile_user_agent)
	}

	/**
	 * Radio button facilitating the selection of the predefined Desktop
	 * User Agent string.
	 */
	private val desktopAgentRadioBtn by lazy {
		userAgentSelectorDialog.view
			.findViewById<RadioButton>(R.id.btn_desktop_user_agent)
	}

	/**
	 * Radio button that triggers the display of the custom User Agent
	 * input field.
	 */
	private val customAgentRadioBtn by lazy {
		userAgentSelectorDialog.view
			.findViewById<RadioButton>(R.id.btn_custom_user_agent)
	}

	/**
	 * Layout container for the custom User Agent input field.
	 * Used to toggle the visibility of the entire input section when
	 * 'Custom' is selected.
	 */
	private val containerAgentEditField by lazy {
		userAgentSelectorDialog.view
			.findViewById<View>(R.id.edit_user_agent_container)
	}

	/**
	 * Input field for manual entry of a custom User Agent string.
	 */
	private val customAgentEditor by lazy {
		userAgentSelectorDialog.view
			.findViewById<EditText>(R.id.edit_user_agent)
	}

	/**
	 * A temporary state holder for the selected User Agent string.
	 *
	 * This property acts as a buffer between the UI state and the final [AIOSettings]
	 * persistence, initialized with the current setting value to ensure a safe
	 * default fallback.
	 */
	private var tempUserAgentHolder: String = aioSetting.browserHttpUserAgent

	/**
	 * Initializes the dialog's interaction layer by binding UI components to asynchronous actions.
	 *
	 * This initializer utilizes a functional mapping strategy to connect view resource IDs
	 * to their corresponding [suspend] logic. By iterating through the clickActions map,
	 * it establishes [View.OnClickListener] instances that bridge the synchronous UI world
	 * with the asynchronous coroutine world.
	 *
	 * Each action is executed within the activityCoroutineScope of the parent Activity,
	 * ensuring that if the Activity is destroyed, any pending dialog operations are
	 * automatically canceled, preventing memory leaks and illegal state exceptions.
	 */
	init {
		userAgentSelectorDialog.view.apply {
			val clickActions: Map<Int, suspend () -> Unit> = mapOf(
				R.id.button_dialog_negative_container to { close() },
				R.id.btn_dialog_positive_container to { applyUserAgent() },
				R.id.btn_mobile_user_agent to { selectMobileUserAgent() },
				R.id.btn_desktop_user_agent to { selectDesktopUserAgent() },
				R.id.btn_custom_user_agent to { selectCustomUserAgent() },
				R.id.edit_user_agent_container to { focusInputField() }
			)

			clickActions.forEach { (viewId, action) ->
				findViewById<View>(viewId).setOnClickListener {
					safeBaseActivity?.activityCoroutineScope?.launch {
						action()
					}
				}
			}
		}
	}

	/**
	 * Synchronizes the internal state with current settings and displays the selector dialog.
	 *
	 * This method initializes [tempUserAgentHolder] with the existing configuration to
	 * ensure state consistency. It then evaluates the current agent string against
	 * defined presets:
	 * 1. **Mobile/Desktop**: Checks the corresponding radio button and hides the
	 * custom editor.
	 * 2. **Custom**: Checks the custom radio button, reveals the editor container,
	 * and populates the field with the current string.
	 *
	 * Finally, it invokes the dialog's show command on the Main thread to ensure
	 * window manager compatibility.
	 */
	suspend fun show() {
		withMainContext {
			tempUserAgentHolder = aioSetting.browserHttpUserAgent
			when (aIOSettings.browserHttpUserAgent) {
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
					customAgentEditor.setText(aIOSettings.browserHttpUserAgent)
				}
			}

			userAgentSelectorDialog.show()
		}
	}

	/**
	 * Dismisses the User Agent selection dialog from the screen.
	 *
	 * This is a thread-safe operation that ensures the [userAgentSelectorDialog]
	 * teardown occurs on the Main thread to prevent window leak exceptions.
	 */
	suspend fun close() {
		withMainContext {
			userAgentSelectorDialog.close()
		}
	}

	/**
	 * Transfers input focus to the custom User Agent editor and requests the keyboard.
	 *
	 * This method highlights the current text for easy replacement and ensures the
	 * [showOnScreenKeyboard] utility is invoked on the Main thread for proper
	 * window focus acquisition.
	 */
	private suspend fun focusInputField() {
		withMainContext {
			customAgentEditor.requestFocus()
			customAgentEditor?.selectAll()
			showOnScreenKeyboard(safeBaseActivity, customAgentEditor)
		}
	}

	/**
	 * Transitions the UI to allow for manual User Agent entry.
	 *
	 * Updates the visibility of the container and editor fields to [View.VISIBLE].
	 * This allows the user to define a unique string that falls outside the
	 * standard mobile or desktop presets.
	 */
	private suspend fun selectCustomUserAgent() {
		withMainContext {
			containerAgentEditField.visibility = View.VISIBLE
			customAgentEditor.visibility = View.VISIBLE
		}
	}

	/**
	 * Configures the selector to use the predefined Desktop User Agent string.
	 *
	 * Updates [tempUserAgentHolder] with [DESKTOP_HTTP_AGENT] and hides the
	 * custom input field to simplify the UI.
	 */
	private suspend fun selectDesktopUserAgent() {
		withMainContext {
			tempUserAgentHolder = DESKTOP_HTTP_AGENT
			customAgentEditor.visibility = View.GONE
		}
	}

	/**
	 * Configures the selector to use the predefined Mobile User Agent string.
	 *
	 * Updates [tempUserAgentHolder] with [MOBILE_HTTP_AGENT] and hides the
	 * custom input field to maintain a clean interface for standard mobile browsing.
	 */
	private suspend fun selectMobileUserAgent() {
		withMainContext {
			tempUserAgentHolder = MOBILE_HTTP_AGENT
			customAgentEditor.visibility = View.GONE
		}
	}

	/**
	 * Finalizes the User Agent selection and persists the choice to the settings.
	 *
	 * This method evaluates whether the user has opted for a preset agent (Mobile/Desktop)
	 * or a custom string.
	 * * Logic flow:
	 * 1. If 'Custom' is selected: Validates that the input field is not empty. If empty,
	 * it triggers haptic feedback and an error toast.
	 * 2. Persistence: Updates the [aIOSettings] instance with the validated string.
	 * 3. Cleanup: Closes the dialog and invokes [onApplyListener] to notify the parent
	 * activity of the change.
	 *
	 * This is a [withMainContext] operation to ensure safe interaction with the
	 * [RadioButton] and [EditText] states.
	 */
	private suspend fun applyUserAgent() = withMainContext {
		val finalAgent = if (customAgentRadioBtn.isChecked) {
			val userEntered = customAgentEditor.text.toString()
			if (userEntered.isEmpty()) {
				safeBaseActivity?.doSomeVibration()
				val msgId = R.string.title_must_enter_valid_user_agent
				showToast(safeBaseActivity, msgId)
				return@withMainContext
			}
			userEntered
		} else {
			tempUserAgentHolder
		}

		aIOSettings.browserHttpUserAgent = finalAgent
		close()
		onApplyListener(aIOSettings)
	}
}