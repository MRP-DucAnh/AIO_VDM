package app.core

import app.core.AIOApp.Companion.aioSettings
import app.core.bases.interfaces.*
import app.core.bases.language.*
import lib.process.*
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import java.util.*

/**
 * Serves as the central coordinator for application-wide language management.
 *
 * This class bridges user language preferences with the technical implementation of
 * runtime language switching. It ensures a seamless multi-language experience by
 * handling the complex activity lifecycle transitions required for language changes.
 *
 * ## Core Responsibilities:
 * - **Language Application**: Applies user-selected languages to the application context.
 * - **Lifecycle Management**: Coordinates activity restarts and application-level refresh
 *   to ensure UI consistency after a language change.
 * - **Language Catalog**: Maintains a list of supported languages with user-friendly display names.
 * - **State Coordination**: Manages flags and commands that orchestrate the language change flow,
 *   preventing race conditions and ensuring a clean UI transition.
 *
 * This class works in concert with a locale management system (like `LocaleAwareManager`) for
 * the technical application of locales and `aioSettings` for persisting user preferences.
 */
open class AIOLanguage {
	
	/**
	 * Logger for this class, used for debugging and tracing the language change lifecycle.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * Provides static constants for supported language codes.
	 *
	 * This companion object holds the ISO 639-1 language codes as constants,
	 * ensuring type-safe and consistent referencing of languages throughout the application.
	 * Using these constants prevents typos and makes the code more readable when
	 * setting or checking language preferences.
	 *
	 * ## Example Usage:
	 * ```kotlin
	 * if (aioSettings.userSelectedUILanguage == AIOLanguage.ENGLISH) {
	 *     // Handle English-specific logic
	 * }
	 * ```
	 */
	companion object {
		
		/** ISO 639-1 language codes for supported languages */
		const val ENGLISH = "en"
		const val BENGALI = "bn"
		const val HINDI = "hi"
		const val TELUGU = "te"
		const val JAPANESE = "ja"
		const val DANISH = "da"
		const val GERMAN = "de"
	}
	
	/**
	 * Provides a catalog of supported languages for the application's UI.
	 *
	 * This list is used to populate language selection menus, allowing users to choose their
	 * preferred language. Each entry combines a system-level language code with a human-readable
	 * display name.
	 *
	 * ### Structure
	 * Each element is a `Pair<String, String>`:
	 * - **`first` (Language Code):** The ISO 639-1 code for the language (e.g., "en", "hi").
	 *   This code is used to configure the application's `Locale`.
	 * - **`second` (Display Name):** A user-friendly string that includes both the English name
	 *   and the native script representation (e.g., "Hindi (हिंदी)").
	 *
	 * ### Rationale for Native Script
	 * Including the language's name in its own script is crucial for usability. It helps users
	 * who may not be fluent in English to easily identify and select their native language.
	 */
	open val languagesList: List<Pair<String, String>> = listOf(
		ENGLISH to "English (Default)",
		HINDI to "Hindi (हिंदी)",
		TELUGU to "Telugu (తెలుగు)",
		BENGALI to "Bengali (বাংলা)",
		JAPANESE to "Japanese (日本語)",
		DANISH to "Danish (Dansk)",
		GERMAN to "German (Deutsch)"
	)
	
	/**
	 * Controls whether the current activity should be finished when it is next resumed.
	 *
	 * This flag is a key part of the language change lifecycle. It is set to `true`
	 * immediately after a new language is applied. When the activity's `onResume()`
	 * is called, it checks this flag. If `true`, the activity finishes itself to allow
	 * the application to restart and redraw the UI with the new locale.
	 *
	 * This mechanism ensures that the language change, which happens at a low level,
	 * is correctly synchronized with the user-facing UI and activity lifecycle,
	 * providing a clean and predictable restart flow.
	 *
	 * @see closeActivityIfLanguageChanged
	 * @see applyUserSelectedLanguage
	 */
	open var finishActivityOnResume = false
	
	/**
	 * A command flag that signals the application should quit to complete a language change.
	 *
	 * When a language is updated, a full application restart is often necessary to ensure
	 * all components, including those in the back stack, adopt the new locale. This flag
	 * triggers the final step of that process.
	 *
	 * It is set to `true` when a language change is initiated and reset to `false` after
	 * the quit command has been executed to prevent accidental repeated shutdowns.
	 */
	private var quitApplicationCommand = false
	
	/**
	 * Applies the user's selected language, preparing the application for a UI refresh.
	 *
	 * This function initiates the language change process by:
	 * 1. Retrieving the user's selected language code from [aioSettings].
	 * 2. Setting a flag (`finishActivityOnResume`) that signals the current activity to
	 *    restart itself during the next `onResume` lifecycle event. This ensures a
	 *    clean UI refresh to display the new language.
	 *
	 * The actual locale change is handled separately by components observing the settings change,
	 * but this function coordinates the necessary activity restart.
	 *
	 * @param afterApplyingLanguage An optional callback invoked immediately after setting up the
	 *                   restart flag. It can be used for tasks that don't depend on the
	 *                   UI being fully refreshed, like showing a temporary "Restarting..." message.
	 */
	fun applyUserSelectedLanguage(
		baseActivityInf: BaseActivityInf?,
		afterApplyingLanguage: () -> Unit = {},
		onLanguageChangeFailed: (String) -> Unit = {}
	) {
		if (baseActivityInf == null) {
			val errorMessage = "applyUserSelectedLanguage(): skipped — activity reference is null"
			logger.d(errorMessage)
			onLanguageChangeFailed.invoke(errorMessage)
			return
		}
		
		// Reset state for new language change operation
		this.finishActivityOnResume = false
		
		// Retrieve user's language preference from persistent storage
		val languageCode = aioSettings.userSelectedUILanguage
		val locale = Locale.forLanguageTag(languageCode)
		
		logger.d("Initiating language change to: $languageCode")
		
		baseActivityInf.getActivity()?.let { activity ->
			if (LocalizeManager.shouldUpdateLocale(activity, locale)){
				// Apply the locale technically through the locale management system
				baseActivityInf.getActivity()?.setLocale(locale)
				
				logger.d("Language applied successfully. System locale set to: $locale")
				
				// Signal that activity should restart to reflect language change
				this.finishActivityOnResume = false
				activity.openPrepareLocalize()
				
				afterApplyingLanguage()
			}
		}
	}
	
	/**
	 * Checks if a language change is pending and, if so, closes the current activity
	 * to trigger a full application restart.
	 *
	 * This method is the crucial second step in the language change process. It should be
	 * called from the `onResume()` lifecycle method of a base activity. It checks the
	 * `finishActivityOnResume` flag, which is set by `applyUserSelectedLanguage`.
	 *
	 * If the flag is `true`, it means a language change has been initiated. The method then:
	 * 1.  Finishes the current activity and its task stack using `finishAffinity()`.
	 * 2.  Sets a command to quit the application (`quitApplicationCommand`).
	 * 3.  Schedules a delayed task to execute the application quit, allowing the current
	 *     activity to close gracefully before the process exits.
	 *
	 * This two-step process (apply, then close/restart) ensures a clean and complete UI refresh
	 * across the entire application, preventing inconsistent language states.
	 *
	 * @param baseActivityInf The activity instance to check. If a language change is
	 *                        detected, this activity (and its task) will be finished.
	 */
	fun closeActivityIfLanguageChanged(baseActivityInf: BaseActivityInf?) {
		baseActivityInf?.getActivity()?.let { safeActivityRef ->
			if (finishActivityOnResume) {
				logger.d("Language change detected. Initiating activity closure and app restart.")
				
				// Finish current activity and all activities below it in the stack
				safeActivityRef.finishAffinity()
				
				// Schedule complete application restart
				quitApplicationCommand = true
				
				// Allow graceful activity destruction before app quit
				delay(300, object : OnTaskFinishListener {
					override fun afterDelay() {
						logger.d("Executing delayed application quit for language change")
						quitApplication(safeActivityRef)
					}
				})
			} else {
				logger.d("No pending language changes - activity remains active")
			}
		} ?: logger.d("Language change check skipped - activity reference is null")
	}
	
	/**
	 * Forces the application to quit, typically to complete a language change process.
	 *
	 * This method ensures a clean application state after a significant change,
	 * like switching languages, which requires rebuilding the entire activity stack.
	 *
	 * It is called with a delay in [closeActivityIfLanguageChanged] to allow the
	 * current activity to finish its lifecycle gracefully before the entire application exits.
	 *
	 * ## Safety Features:
	 * - **Command Flag**: Only executes if [quitApplicationCommand] is true, preventing accidental calls.
	 * - **Null Safety**: Safely handles cases where the activity reference might be null.
	 * - **Stack Clearing**: Uses `finishAffinity()` to clear the entire activity stack reliably.
	 *
	 * @param baseActivityInf The activity context used to execute the quit command.
	 */
	private fun quitApplication(baseActivityInf: BaseActivityInf?) {
		baseActivityInf?.getActivity()?.let { safeActivityRef ->
			if (quitApplicationCommand) {
				logger.d("Executing application quit for language change completion")
				
				// Reset command to prevent multiple executions
				quitApplicationCommand = false
				
				// Clear entire activity stack and exit application
				safeActivityRef.finishAffinity()
			} else {
				logger.d("Application quit cancelled - command flag was reset")
			}
		} ?: logger.d("Application quit aborted - activity reference is null")
	}
}