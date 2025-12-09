@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.content.*
import android.content.res.*
import android.os.*
import android.preference.*
import androidx.core.content.*
import app.core.*
import app.core.bases.language.LocalizeManager.getLocale
import app.core.bases.language.LocalizeManager.registerListener
import app.core.bases.language.LocalizeManager.setLocale
import app.core.bases.language.LocalizeManager.unregisterListener
import app.core.bases.language.LocalizeManager.updateResources
import java.util.*

/**
 * A singleton object responsible for managing the application's localization.
 *
 * This manager provides a comprehensive set of tools to handle in-app language switching. It allows
 * for persisting the user's selected language, applying the locale to the application's context,
 * and broadcasting changes to update the UI dynamically.
 *
 * Key features include:
 * - Persisting language preferences using `SharedPreferences`.
 * - Attaching a localized `Context` to Activities and the Application via `onAttach`.
 * - Methods to set and get the current language (`setLocale`, `getLanguage`).
 * - Support for both modern and legacy methods of updating resources to ensure compatibility across
 *   different Android API levels.
 * - A broadcast mechanism to notify components (like Activities) of locale changes, enabling them
 *   to recreate and apply the new language settings.
 *
 * Typical usage involves calling `onAttach` in the `attachBaseContext` method of your base
 * `Activity` or `Application` class, and using `setLocale` to change the language at runtime,
 * often followed by an activity recreation to reflect the changes.
 */
object LocalizeManager {
	
	/**
	 * SharedPreferences key for the selected language.
	 * This key is used to persist the user's chosen language code (e.g., "en", "fr")
	 * so that it can be retrieved and applied across app sessions.
	 */
	private const val SELECTED_LANGUAGE = "Locale.Manager.Selected.Language"
	
	/**
	 * A list of listeners that are invoked when the application's locale changes.
	 *
	 * This allows other parts of the application to subscribe to language changes without
	 * relying on the broadcast mechanism. When the locale is updated, each listener in this
	 * list will be called, receiving the new `Locale` object. This is useful for
	 * non-Activity components or for UI updates that need to happen immediately without
	 * an Activity recreation.
	 *
	 * Example usage:
	 * ```
	 * val listener: (Locale) -> Unit = { newLocale ->
	 *     // Update UI or state with the new locale
	 * }
	 *
	 * // On start
	 * LocalizeManager.listeners.add(listener)
	 *
	 * // On stop
	 * LocalizeManager.listeners.remove(listener)
	 * ```
	 */
	private val listeners = mutableSetOf<LanguageChangeListener>()
	
	/**
	 * Registers a listener to be notified of language changes.
	 *
	 * This method adds the provided [listener] to a set of active listeners. Whenever the
	 * application's locale is updated through this manager, all registered listeners will be
	 * invoked with the new `Locale`. This provides a direct callback mechanism for components
	 * that need to react to language changes without relying on a `BroadcastReceiver`.
	 *
	 * It is crucial to unregister the listener using [unregisterListener] when it's no longer
	 * needed (e.g., in an `onDestroy` or `onStop` lifecycle method) to prevent memory leaks.
	 *
	 * @param listener A `LanguageChangeListener` (which is a type alias for `(Locale) -> Unit`)
	 *   that will be executed when the locale changes.
	 * @see unregisterListener
	 */
	fun registerListener(listener: LanguageChangeListener) {
		listeners.add(listener)
	}
	
	/**
	 * Removes a previously registered [LanguageChangeListener] from the list of listeners.
	 *
	 * This function allows a component to stop receiving notifications about locale changes.
	 * It's important to call this method when the component is destroyed or no longer needs
	 * to be aware of language updates to prevent memory leaks.
	 *
	 * @param listener The listener instance to be removed.
	 *
	 * @see registerListener
	 */
	fun unregisterListener(listener: LanguageChangeListener) {
		listeners.remove(listener)
	}
	
	/**
	 * Attaches a new configuration to the given [Context] with the persisted locale,
	 * or the device's default locale if no language has been persisted.
	 *
	 * This method is intended to be called in the `attachBaseContext` of an Activity or Application.
	 * It retrieves the previously saved language code from SharedPreferences. If no language
	 * is found, it falls back to the system's default language. It then returns a new
	 * context with the appropriate locale configuration applied.
	 *
	 * @param context The base context to be updated.
	 * @return A new [Context] with the updated locale configuration.
	 */
	fun onAttach(context: Context): Context {
		val persistedLanguageCode = getPersistedData(context, Locale.getDefault().language)
		return setLocale(context, persistedLanguageCode)
	}
	
	/**
	 * Attaches the localization context to an Activity or Application, sets the language,
	 * and returns a new Context with the updated configuration. This method should be
	 * called in `attachBaseContext`.
	 *
	 * It retrieves the persisted language from SharedPreferences. If no language is found,
	 * it falls back to the provided `defaultLanguage`.
	 *
	 * @param context The base context from the Activity or Application.
	 * @param defaultLanguage The language code (e.g., "en", "fr") to use if no language
	 *        has been previously persisted.
	 * @return A new `Context` object with the locale updated.
	 */
	fun onAttach(context: Context, defaultLanguage: String): Context {
		val persistedLanguage = getPersistedData(context, defaultLanguage)
		return setLocale(context, persistedLanguage)
	}
	
	/**
	 * Retrieves the currently selected language code.
	 *
	 * This function fetches the language code that has been persisted in SharedPreferences.
	 * If no language has been explicitly set and persisted, it defaults to the
	 * current language of the device's default locale.
	 *
	 * @param context The context to use for accessing SharedPreferences.
	 * @return A string representing the language code (e.g., "en", "fr").
	 */
	fun getLanguage(context: Context): String {
		return getPersistedData(context, Locale.getDefault().language)
	}
	
	/**
	 * Sets the application's locale.
	 *
	 * This function persists the chosen language and then updates the application's
	 * configuration to reflect the new locale. It handles different Android versions
	 * by calling the appropriate resource update method.
	 *
	 * @param context The context from which to get resources and SharedPreferences.
	 * @param language The language code (e.g., "en", "fr") to set.
	 * @return A new context with the updated configuration.
	 */
	fun setLocale(context: Context, language: String): Context {
		persist(context, language)
		return updateResources(context, language)
	}
	
	/**
	 * Sets the application's locale by persisting it and notifying listeners of the change.
	 *
	 * This method takes a `Locale` object, saves its language code to `SharedPreferences`
	 * for persistence across app sessions, and then updates the application's resources
	 * to reflect the new language. It also invokes all registered `LanguageChangeListener`
	 * instances, providing them with the new locale.
	 *
	 * This is a key method for triggering a runtime language switch. After calling this,
	 * you typically need to recreate UI components (like Activities) for the changes
	 * to take full effect visually.
	 *
	 * @param context The context used for accessing `SharedPreferences` and updating resources.
	 * @param locale The new `Locale` to be set for the application.
	 * @see setLanguage
	 * @see updateResources
	 * @see registerListener
	 */
	fun setLocale(context: Context, locale: Locale) {
		persist(context, locale.language)
		updateResources(context, locale.language)
		listeners.forEach { listener ->
			listener.onLanguageChanged(locale)
		}
	}
	
	/**
	 * Retrieves the persisted language code from SharedPreferences.
	 * If no language code is found, it returns the provided default language.
	 *
	 * @param context The context to access SharedPreferences.
	 * @param defaultLanguage The language code to return if none is persisted.
	 * @return The persisted language code or the default one.
	 */
	private fun getPersistedData(context: Context, defaultLanguage: String): String {
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		return preferences.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
	}
	
	/**
	 * Persists the selected language code to the DataStore.
	 *
	 * This is a suspend function that asynchronously saves the provided language code
	 * (e.g., "en", "fr") into the application's settings using Jetpack DataStore.
	 * This allows the chosen language to be retrieved later, even after the app is restarted.
	 *
	 * @param context The context used to access the DataStore.
	 * @param language The language code string to save.
	 */
	private fun persist(context: Context, language: String) {
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		preferences.edit(commit = true) {
			putString(SELECTED_LANGUAGE, language)
		}
	}
	
	/**
	 * Creates a new context with an updated configuration for the specified language.
	 *
	 * This function takes a language code, creates a `Locale` from it, and sets this
	 * new locale as the default for the JVM. It then updates the `Configuration` of the
	 * provided context to use the new locale and its corresponding layout direction.
	 * Finally, it returns a new `Context` with this updated configuration, which should
	 * be used to ensure all resources are loaded correctly for the new language.
	 *
	 * This method is the standard way to update resources for Android API 17 (Jelly Bean MR1)
	 * and above.
	 *
	 * @param context The current context.
	 * @param language The language code (e.g., "en", "ar") to apply.
	 * @return A new [Context] with the locale configuration updated.
	 */
	private fun updateResources(context: Context, language: String): Context {
		val locale = Locale(language)
		Locale.setDefault(locale)
		val configuration = context.resources.configuration
		configuration.setLocale(locale)
		configuration.setLayoutDirection(locale)
		return context.createConfigurationContext(configuration)
	}
	
	/**
	 * Updates the application's resources with a new locale, using a legacy method
	 * suitable for Android versions older than Nougat (API 24).
	 *
	 * This function is marked as deprecated because it mutates the `Resources` object
	 * directly via `updateConfiguration`, which is discouraged in newer Android versions.
	 * For modern implementations, `Context.createConfigurationContext()` is preferred.
	 *
	 * It sets the default `Locale` for the JVM, updates the `Configuration` of the
	 * provided context's resources, and, for API 17+, sets the layout direction.
	 *
	 * @param context The context whose resources need to be updated.
	 * @param language The language code (e.g., "en", "fr") to apply.
	 * @return The original `Context`, now with its resources' configuration updated.
	 *         Note that unlike `createConfigurationContext`, this method modifies the
	 *         existing context's resources and returns the same context instance.
	 */
	@Deprecated("This method is deprecated")
	private fun updateResourcesLegacy(context: Context, language: String): Context {
		val locale = Locale(language)
		Locale.setDefault(locale)
		val resources: Resources = context.resources
		val configuration: Configuration = resources.configuration
		configuration.locale = locale
		configuration.setLayoutDirection(locale)
		resources.updateConfiguration(configuration, resources.displayMetrics)
		return context
	}
	
	/**
	 * Determines whether the application's locale needs to be updated.
	 *
	 * This function compares the language of the provided `newLocale` with the currently
	 * configured locale in the given `context`. It returns `true` if the languages are
	 * different, indicating that a locale update is necessary. This is useful for avoiding
	 * unnecessary resource reloads or UI recreations when the selected language is already
	 * active.
	 *
	 * @param context The current context, used to get the existing configuration.
	 * @param newLocale The new `Locale` to compare against the current one.
	 * @return `true` if the new locale's language is different from the current one,
	 *         `false` otherwise.
	 */
	fun shouldUpdateLocale(context: Context, newLocale: Locale): Boolean {
		val currentLocale = getLocale(context)
		return currentLocale.language != newLocale.language ||
			currentLocale.country != newLocale.country
	}
	
	/**
	 * Extracts the updated [Configuration] from a broadcast [Intent].
	 *
	 * This function is designed to be used in a `BroadcastReceiver` that listens for
	 * locale change events. It checks if the provided [Intent] has the specific action
	 * `LocalizeConstant.ON_LOCALE_CHANGED_ACTION`. If it does, it attempts to
	 * retrieve a `Configuration` object that was passed as a parcelable extra
	 * under the key `LocalizeConstant.CONFIGURATION_KEY`.
	 *
	 * @param intent The [Intent] received by the broadcast receiver, which may contain
	 *               the updated configuration. Can be null.
	 * @return The new [Configuration] object if it was successfully extracted,
	 *         otherwise `null`.
	 */
	fun getConfigurationChanged(intent: Intent?): Configuration? {
		if (intent?.action?.isEmpty() == false) {
			if (intent.action == LocalizeConstant.ON_LOCALE_CHANGED_ACTION) {
				val bundle = intent.extras
				return if (bundle != null) {
					bundle.getParcelable<Parcelable>(
						LocalizeConstant.CONFIGURATION_KEY
					) as? Configuration
				} else null
			}
		}
		return null
	}
	
	/**
	 * Changes the application's locale and returns the new configuration.
	 *
	 * This function first persists the new language preference using the provided [locale].
	 * It then updates the application's resources to reflect this new locale, creating
	 * and returning a new [Configuration] object if the locale was successfully changed.
	 * This new configuration can be used to update the context or broadcast the change.
	 *
	 * @param context The context used for persisting the language and updating resources.
	 * @param locale The new [Locale] to apply to the application.
	 * @return A new [Configuration] object with the updated locale, or `null` if the
	 *   locale was already set to the new one and no change was needed.
	 */
	fun changeLocale(context: Context, locale: Locale): Configuration? {
		setLocale(context, locale)
		return setLocale(context)
	}
	
	/**
	 * Retrieves the currently configured [Locale] for the application.
	 *
	 * This function reads the language code from [SharedPreferences]. If a language code
	 * has not been previously saved, it falls back to the default language specified
	 * in [Localize.DEFAULT].
	 *
	 * @param context The context used to access [SharedPreferences].
	 * @return The [Locale] object corresponding to the saved language, or the default locale.
	 */
	fun getLocale(context: Context): Locale {
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
		val defValue = AIOApp.aioLanguage.languagesList[0].first
		val lang = sharedPreferences.getString(LocalizeConstant.PREF_LANGUAGE_KEY, defValue)
			?: defValue
		return Locale(lang)
	}
	
	/**
	 * Updates the application's configuration with the currently persisted locale.
	 *
	 * This function retrieves the saved locale using [getLocale]. If the current
	 * configuration's locale is different from the saved one, it updates the
	 * configuration with the new locale, persists the change, and applies it to the
	 * application's resources.
	 *
	 * This method is deprecated and uses `updateConfiguration` which is deprecated on
	 * newer API levels. Consider using `Context.createConfigurationContext` instead.
	 *
	 * @param context The context from which to access resources and SharedPreferences.
	 * @return A new [Configuration] object with the updated locale if a change was made,
	 *         otherwise `null`.
	 */
	fun setLocale(context: Context): Configuration? {
		val resources: Resources = context.resources
		val configuration: Configuration = resources.configuration
		val locale: Locale = getLocale(context)
		if (configuration.locale != locale) {
			configuration.setLocale(locale)
			setLocale(context, locale)
			resources.updateConfiguration(configuration, resources.displayMetrics)
			return configuration
		}
		return null
	}
	
	/**
	 * Initializes the application's locale based on the persisted language setting.
	 *
	 * This function retrieves the saved locale using [getLocale] and then applies it
	 * to the application's context by calling [setLocale] with the corresponding
	 * language code. It's a convenient way to ensure the correct language is set
	 * when the application starts or when a configuration change requires it.
	 *
	 * @param context The context used for retrieving the saved locale and applying the new one.
	 */
	fun initLocale(context: Context) {
		val locale = getLocale(context)
		setLocale(context, locale.language)
	}
}
