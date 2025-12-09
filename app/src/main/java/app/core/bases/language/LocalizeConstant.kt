package app.core.bases.language

/**
 * Holds constant values used throughout the application for localization and language settings.
 * This object centralizes keys and settings related to language preferences, broadcast actions,
 * and timing delays for configuration changes.
 */
object LocalizeConstant {
	
	/**
	 * Key for storing the selected language code in SharedPreferences.
	 * The value associated with this key will be a language tag like "en", "es", etc.
	 */
	const val PREF_LANGUAGE_KEY = "language"
	
	/**
	 * Action string for the broadcast sent when the application's locale is changed.
	 * Components can register a [android.content.BroadcastReceiver] with this action
	 * to be notified of locale updates and refresh their UI accordingly.
	 */
	const val ON_LOCALE_CHANGED_ACTION = "ON_LOCALE_CHANGED_ACTION"
	
	/**
	 * Key used to store and retrieve the [android.content.res.Configuration] object from a Bundle.
	 * This is particularly useful for saving and restoring the application's locale configuration
	 * during state changes, such as orientation changes, to ensure the correct language is maintained.
	 */
	const val CONFIGURATION_KEY = "CONFIGURATION_KEY"
	
	/**
	 * A delay in milliseconds before finishing the activity to ensure the UI has time to update
	 * after a locale change. This helps prevent visual glitches during the transition.
	 */
	const val PREPARE_DELAY = 300L
	
	/**
	 * Delay in milliseconds before finishing the activity after a language change.
	 * This provides a smoother user experience by allowing time for UI updates to complete.
	 */
	const val FINISH_DELAY = 500L
}