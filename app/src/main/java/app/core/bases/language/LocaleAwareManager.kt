@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.core.content.edit
import java.util.Locale

/**
 * Manages application locale configuration and persistence for runtime language switching.
 *
 * This class serves as the central engine for implementing multi-language support in the app.
 * It handles the complete lifecycle of locale management from persistence to resource configuration.
 *
 * ## How Locale Management Works:
 *
 * **Persistence Layer**: Uses SharedPreferences to remember the user's language choice
 * across app launches. The language setting survives app restarts and device reboots.
 *
 * **Configuration Context**: Creates a new context wrapper with updated locale settings
 * that automatically applies to all resource loading (strings, layouts, dimensions).
 *
 * **Layout Direction Support**: Automatically handles right-to-left (RTL) languages
 * like Arabic and Hebrew by configuring layout direction alongside the locale.
 *
 * ## Important Deprecation Notes:
 *
 * The deprecated Configuration constructor and setLocale() method are used for
 * broader Android version compatibility. Modern alternatives like setLocales()
 * are only available in API 24+, while this approach supports older devices.
 */
class LocaleAwareManager(private val context: Context) {

	/**
	 * SharedPreferences instance for persisting language preferences.
	 *
	 * **Why application context**: Using application context prevents memory leaks
	 * that could occur if activity contexts were used and activities were destroyed.
	 *
	 * **Commit vs Apply**: This implementation uses commit=true for immediate writing
	 * to ensure language changes are persisted before configuration updates.
	 */
	private val preferences: SharedPreferences = getDefaultSharedPreferences(context)

	/**
	 * Applies the currently saved locale to the application context.
	 *
	 * **Typical Use Case**: Called during app startup or activity creation to
	 * ensure the app displays in the user's previously selected language.
	 *
	 * **Flow**:
	 * 1. Reads persisted language from SharedPreferences
	 * 2. Creates locale configuration with the saved language
	 * 3. Returns context wrapper that applies the locale to all resources
	 *
	 * @return Context wrapper configured with the user's preferred locale
	 */
	fun setLocale(): Context {
		return updateResources(language)
	}

	/**
	 * Changes the application locale and persists the new setting.
	 *
	 * **Complete Language Update Process**:
	 * 1. Immediately persists the new language to SharedPreferences
	 * 2. Updates the default Locale for Java-formatted strings
	 * 3. Creates and returns a context configured with the new locale
	 *
	 * **Immediate Persistence**: Uses commit=true to ensure the language setting
	 * is written to disk before the context is reconfigured, preventing scenarios
	 * where the app might crash and lose the user's language selection.
	 *
	 * @param language ISO language code (e.g., "en", "fr", "ar")
	 * @return Context wrapper ready to be used for resource loading
	 */
	fun setNewLocale(language: String): Context {
		persistLanguage(language)
		return updateResources(language)
	}

	/**
	 * Retrieves the currently active language setting.
	 *
	 * **Default Language**: Returns English ("en") if no language preference
	 * has been saved yet, ensuring the app has a valid fallback.
	 *
	 * **Persistence**: The value is stored in SharedPreferences and survives
	 * app restarts, maintaining user preference across sessions.
	 */
	val language: String
		get() = preferences.getString(LANGUAGE_KEY, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH

	/**
	 * Persists the language selection to SharedPreferences with immediate effect.
	 *
	 * **Why commit=true**: While apply() is usually preferred for performance,
	 * we use commit() here to ensure the language is definitely saved before
	 * we proceed with context recreation. This prevents data loss if the app
	 * crashes during the language change process.
	 *
	 * @param language The ISO language code to persist
	 */
	@SuppressLint("ApplySharedPref")
	private fun persistLanguage(language: String) {
		preferences.edit(commit = true) {
			putString(LANGUAGE_KEY, language)
		}
	}

	/**
	 * Creates a new context configured with the specified locale.
	 *
	 * **Key Operations**:
	 * 1. Sets the default Locale for Java formatting operations
	 * 2. Creates a Configuration with the new locale
	 * 3. Applies layout direction for RTL language support
	 * 4. Returns a context wrapper that uses the new configuration
	 *
	 * **Layout Direction**: Automatically handles text direction for languages
	 * that read right-to-left. The system will automatically flip layout
	 * constraints and view positioning for RTL languages.
	 *
	 * @param language The target language code for configuration
	 * @return New context instance with locale-specific resource configuration
	 */
	private fun updateResources(language: String): Context {
		// Create Locale instance from language code
		val locale = Locale(language)

		// Set default locale for Java formatters (Date, Number, etc.)
		Locale.setDefault(locale)

		// Create configuration with new locale settings
		val config = Configuration(context.resources.configuration)
		config.setLocale(locale)          // Apply language to resources
		config.setLayoutDirection(locale) // Apply RTL support if needed

		// Return context that will use this configuration for all resource access
		return context.createConfigurationContext(config)
	}

	companion object {
		/**
		 * Default language code used when no user preference exists.
		 *
		 * English is chosen as the default for maximum compatibility
		 * and as a common fallback for most international applications.
		 */
		const val LANGUAGE_ENGLISH = "en"

		/**
		 * SharedPreferences key for storing language preference.
		 *
		 * Using a descriptive key prevents conflicts with other preferences
		 * that might be stored in the same SharedPreferences file.
		 */
		private const val LANGUAGE_KEY = "language_key"

		/**
		 * Retrieves the current locale from resources.
		 *
		 * **Modern Alternative**: Uses configuration.locales[0] which is
		 * the recommended approach post-API 24, replacing the deprecated
		 * configuration.locale while maintaining compatibility.
		 *
		 * @param res The Resources instance to extract locale from
		 * @return The current locale configured in the resources
		 */
		fun getLocale(res: Resources): Locale = res.configuration.locales[0]
	}
}