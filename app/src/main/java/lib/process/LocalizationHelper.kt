@file:Suppress("DEPRECATION")

package lib.process

import android.content.*
import android.content.res.*
import lib.process.LocalizationHelper.getLocalizedString
import lib.process.LocalizationHelper.setAppLocale
import lib.process.LocalizationHelper.updateResources
import java.util.*

/**
 * Utility object for managing application localization and locale switching at runtime.
 *
 * This helper class provides methods to dynamically change the application's language/locale
 * without requiring a device restart or system-wide setting changes. It's particularly useful
 * for applications that offer in-app language selection features.
 *
 * Key features:
 * - Set a custom locale for the entire application
 * - Retrieve localized strings based on the currently selected locale
 * - Maintains the selected locale across configuration changes (when properly persisted)
 * - Handles resource configuration updates safely
 *
 * Note: The current locale state is stored in memory only. For persistence across app
 * restarts, you should save the selected locale to SharedPreferences and re-apply it
 * during app initialization.
 */
object LocalizationHelper {

	/**
	 * Stores the currently active locale for the application.
	 * This is used to determine which language resources should be loaded.
	 * Null indicates that the system default locale is being used.
	 */
	private var currentLocale: Locale? = null

	/**
	 * Sets the application's locale to the specified locale and updates all
	 * resources accordingly.
	 *
	 * This method changes the locale for the entire application context, affecting
	 * all subsequent resource lookups (strings, plurals, formatted dates, etc.).
	 * The change is applied immediately to the provided Context's resources.
	 *
	 * The method stores the selected locale in memory for future reference.
	 * For the change to persist across application restarts, you should save the
	 * locale code (e.g., "en_US") to SharedPreferences and call this method again
	 * during app initialization.
	 *
	 * Usage example:
	 * ```kotlin
	 * // Switch to Spanish
	 * LocalizationHelper.setAppLocale(context, Locale("es"))
	 *
	 * // Switch to US English
	 * LocalizationHelper.setAppLocale(context, Locale("en", "US"))
	 * ```
	 *
	 * @param context The application or activity context whose resources should
	 *                be updated. It's recommended to use the ApplicationContext
	 *                to avoid memory leaks.
	 *
	 * @param locale The desired locale to apply (e.g., Locale("fr") for French,
	 *               Locale("zh", "CN") for Simplified Chinese).
	 *
	 * @see getLocalizedString
	 * @see updateResources
	 */
	@JvmStatic
	fun setAppLocale(context: Context, locale: Locale) {
		currentLocale = locale
		updateResources(context, locale)
	}

	/**
	 * Retrieves a localized string resource based on the currently set application
	 * locale. This method returns the string for the given resource ID, using the
	 * locale that was previously set via [setAppLocale]. If no custom locale has
	 * been set (currentLocale is null), it falls back to the system's default locale
	 * resources.
	 *
	 * The method creates a temporary ConfigurationContext with the desired locale to
	 * ensure the correct localized string is returned without affecting the original
	 * Context's configuration permanently.
	 *
	 * This is particularly useful when you need to display strings in the user's
	 * selected language while keeping the rest of the UI in another language, or
	 * when you need to preview strings in different locales.
	 *
	 * Usage example:
	 * ```kotlin
	 * // Assuming app locale was set to Spanish
	 * val welcomeText = LocalizationHelper.getLocalizedString(context, R.string.welcome)
	 * // welcomeText will be in Spanish even if device is set to English
	 * ```
	 *
	 * Performance note: This method creates a new ConfigurationContext on each call.
	 * For multiple string lookups, consider caching the configurationContext or using
	 * the already-updated context from setAppLocale.
	 *
	 * @param context The base context to use for resource lookup. Must not be null.
	 * @param resId The resource ID of the string to retrieve (e.g., R.string.hello_world).
	 * @return The localized string in the currently selected application locale,
	 *         or the system default locale if no custom locale is set.
	 * @see setAppLocale
	 */
	@JvmStatic
	fun getLocalizedString(context: Context, resId: Int): String {
		val resources = context.resources
		return if (currentLocale != null) {
			val config = Configuration(resources.configuration)
			config.setLocale(currentLocale)
			val configurationContext = context.createConfigurationContext(config)
			configurationContext.resources.getString(resId)
		} else {
			resources.getString(resId)
		}
	}

	/**
	 * Internal method that applies a new locale configuration to the application's
	 * resources. This method creates a new Configuration object based on the existing
	 * resource configuration, sets the desired locale, and then applies the updated
	 * configuration to the resources.
	 *
	 * Important implementation notes:
	 * - The method creates a copy of the existing configuration to avoid modifying
	 *   the original before applying changes.
	 * - The updateConfiguration method is deprecated as of Android API level 25 (Android 7.0),
	 *   but remains the standard approach for runtime locale changes in older Android versions.
	 *   For Android 7.0+, consider using createConfigurationContext() for better compatibility.
	 * - This method does NOT persist the locale setting. That responsibility belongs to the caller.
	 *
	 * Technical details:
	 * The update happens at the Resources level, which means:
	 * - All subsequent resource lookups through this Context will use the new locale
	 * - The change affects the specific Context's resources, not globally across the app
	 * - For application-wide effect, pass the ApplicationContext to this method
	 *
	 * @param context The context whose resources should be updated with the new locale.
	 * @param locale The locale to apply to the context's resources.
	 * @see setAppLocale
	 */
	@JvmStatic
	private fun updateResources(context: Context, locale: Locale) {
		val resources = context.resources
		val configuration = resources.configuration
		val config = Configuration(configuration)
		config.setLocale(locale)
		resources.updateConfiguration(config, resources.displayMetrics)
	}
}