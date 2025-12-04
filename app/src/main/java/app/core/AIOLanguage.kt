package app.core

import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.interfaces.*
import app.core.bases.language.*
import lib.process.*
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import java.util.*

/**
 * [AIOLanguage] serves as the central coordinator for application-wide language management.
 *
 * This class bridges user language preferences with the technical implementation of
 * runtime language switching, providing a seamless multi-language experience while
 * handling the complex activity lifecycle transitions required for language changes.
 *
 * ## Core Responsibilities:
 * - **Language Application**: Applies user-selected languages to both current activity
 *   and application context through [LocaleAwareManager]
 * - **Lifecycle Management**: Handles activity restarts and application quitting when
 *   language changes require complete UI refresh
 * - **Language Catalog**: Maintains the list of supported languages with proper display names
 * - **State Coordination**: Manages flags and commands that control the language change flow
 *
 * The class works in concert with [LocaleAwareManager] for technical locale application
 * and [aioSettings] for persistence of user preferences.
 */
open class AIOLanguage {

	/**
	 * Logger for this class, used for debugging and tracing the language change lifecycle.
	 *
	 * Provides detailed logs for events like language application initiation,
	 * activity restarts, and application quit commands, which is crucial for
	 * diagnosing issues in the complex language switching flow.
	 */
	private val logger = LogHelperUtils.from(javaClass)

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
	 * Catalog of supported languages with user-friendly display names.
	 *
	 * **Format**: Each entry is a Pair of (languageCode, displayName) where:
	 * - languageCode: ISO 639-1 code used for system locale configuration
	 * - displayName: User-visible name with native script representation
	 *
	 * **Why Include Native Script**: Shows users how their language will appear
	 * in the interface, helping them identify their preferred language even if
	 * they can't read the English language name.
	 */
	val languagesList: List<Pair<String, String>> = listOf(
		ENGLISH to "English (Default)",
		HINDI to "Hindi (हिंदी)",
		TELUGU to "Telugu (తెలుగు)",
		BENGALI to "Bengali (বাংলা)",
		JAPANESE to "Japanese (日本語)",
		DANISH to "Danish (Dansk)",
		GERMAN to "German (Deutsch)"
	)

	/**
	 * Controls whether the current activity should finish when resumed.
	 *
	 * **Usage Scenario**: Set to true after applying a language change that
	 * requires the activity to restart. When the activity resumes, it checks
	 * this flag and finishes itself if needed, triggering the app restart flow.
	 *
	 * **Lifecycle Coordination**: This flag bridges the gap between language
	 * application and activity destruction, ensuring clean state transitions.
	 */
	open var finishActivityOnResume = false

	/**
	 * Signals that the application should completely quit.
	 *
	 * **Why Quit Entire App**: Some language changes require restarting all
	 * activities to ensure consistent language across the entire app state.
	 * This flag triggers a complete application shutdown and restart.
	 *
	 * **Safety Mechanism**: The flag is reset after being processed to prevent
	 * accidental multiple quit commands.
	 */
	private var quitApplicationCommand = false

	/**
	 * Applies the user's language selection to both activity and application context.
	 *
	 * ## Complete Language Application Process:
	 * 1. Retrieves the stored language preference from [aioSettings]
	 * 2. Applies the locale through [LocaleAwareManager] for technical configuration
	 * 3. Sets up activity finishing flags for proper UI refresh
	 * 4. Invokes completion callback when the process is finished
	 *
	 * **Why Two-Step Process**: The language is applied immediately for technical
	 * correctness, but activity restart is deferred to avoid interrupting user flow
	 * and to handle the complex activity stack transitions properly.
	 *
	 * @param onComplete Callback invoked after successful language application,
	 *                   useful for updating UI state or showing confirmation
	 */
	fun applyUserSelectedLanguage(onComplete: () -> Unit = {}) {
		// Reset state for new language change operation
		this.finishActivityOnResume = false

		// Retrieve user's language preference from persistent storage
		val languageCode = aioSettings.userSelectedUILanguage
		val locale = Locale.forLanguageTag(languageCode)

		logger.d("Initiating language change to: $languageCode")

		// Apply the locale technically through the locale management system
		INSTANCE.localeAwareManager?.setNewLocale(languageCode)

		logger.d("Language applied successfully. System locale set to: $locale")

		// Signal that activity should restart to reflect language change
		this.finishActivityOnResume = true

		onComplete()
	}

	/**
	 * Checks if a language change occurred and closes the activity if needed.
	 *
	 * **Typical Usage**: Called from Activity's onResume() method to detect
	 * if a language change was applied while the activity was paused or stopped.
	 *
	 * **Flow Control**:
	 * - If language was changed: Finish activity and schedule app quit
	 * - If no change: Continue normal activity operation
	 *
	 * **Why Delay Application Quit**: Provides a smooth transition by allowing
	 * the current activity to finish cleanly before quitting the entire app.
	 *
	 * @param baseActivityInf The activity that should check for language changes
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
	 * Forces application quit to complete language change process.
	 *
	 * **Why Complete Restart Needed**: Some language changes require rebuilding
	 * the entire activity stack to ensure all components use the new language.
	 * This method ensures a clean application state after language switch.
	 *
	 * **Safety Features**:
	 * - Command flag prevents multiple executions
	 * - Null safety for activity reference
	 * - Affinity finishing clears entire activity stack
	 *
	 * @param baseActivityInf The activity context used to quit the application
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