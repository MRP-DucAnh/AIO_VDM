@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.content.*
import android.content.res.*
import android.os.*
import androidx.appcompat.app.*
import java.util.*

/**
 * An `AppCompatActivity` that provides foundational support for in-app language switching.
 *
 * This abstract base class simplifies the process of implementing dynamic localization within an
 * Android application. It transparently handles the critical lifecycle events and context
 * wrapping required to ensure that the correct language resources are loaded and displayed.
 *
 * Key responsibilities of this class include:
 * - **Context Wrapping:** In `attachBaseContext`, it wraps the activity's base context with a
 *   new context configured for the currently selected locale. This is the cornerstone of
 *   in-app language switching, ensuring all resource lookups (strings, layouts, etc.)
 *   use the correct language.
 * - **Lifecycle Management for Locale Changes:** It registers a `BroadcastReceiver` to listen
 *   for locale change events initiated by `LocalizeManager`. Upon receiving a broadcast,
 *   it triggers the activity's `recreate()` method, forcing the UI to be redrawn with the
 *   new language resources.
 * - **Configuration Change Handling:** It properly handles `onConfigurationChanged` to apply
 *   locale updates and ensures the activity is recreated smoothly.
 *
 * ### Usage
 * To create an activity that supports on-the-fly language changes, simply extend this class
 * instead of the standard `AppCompatActivity`.
 *
 *
 * @see LocaleManagerInf
 * @see LocalizeManager
 */
open class LocaleActivityInf : AppCompatActivity(), LocaleManagerInf, LanguageChangeListener {
	
	/**
	 * Called when the activity is first created.
	 *
	 * This implementation calls the superclass's `onCreate` method and then registers a
	 * `BroadcastReceiver` to listen for locale change events. This allows the activity
	 * to automatically update its configuration and UI when the application's language is changed.
	 *
	 * @param savedInstanceState If the activity is being re-initialized after previously being
	 * shut down then this Bundle contains the data it most recently supplied in
	 * `onSaveInstanceState(Bundle)`. Otherwise it is null.
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		LocalizeManager.registerListener(this)
	}
	
	/**
	 * Callback method invoked when the application's language is changed.
	 *
	 * This function is called by the [LocalizeManager] whenever a new locale is set,
	 * allowing the activity to react to the change. By default, this implementation is empty,
	 * as the core language update mechanism is handled by recreating the activity via a
	 * broadcast receiver.
	 *
	 * Subclasses can override this method to perform additional actions immediately after
	 * a language change is initiated but before the activity is recreated, such as updating
	 * non-view components or analytics events.
	 *
	 * @param newLocale The new [Locale] that has been set for the application.
	 * @see LocalizeManager.setLocale
	 * @see LanguageChangeListener
	 */
	override fun onLanguageChanged(newLocale: Locale) {
		updateActivityConfiguration(newLocale)
		Handler(Looper.getMainLooper())
			.postDelayed({ recreate() }, LocalizeConstant.PREPARE_DELAY)
	}
	
	/**
	 * Updates the activity's configuration to reflect the new locale.
	 *
	 * This function is intended to be called when the locale changes. It manually updates the
	 * `resources` and `applicationContext` of the activity to use the new language configuration.
	 * This ensures that any subsequent resource lookups within the current activity instance
	 * will use the correct language, even before the activity is fully recreated.
	 *
	 * This is a lower-level utility function. The primary mechanism for language updates in
	 * this class is handled by `recreate()`, which is triggered in `onLanguageChanged`.
	 *
	 * @param newLocale The new [Locale] to apply to the activity's configuration.
	 */
	private fun updateActivityConfiguration(locale: Locale) {
		val configuration = resources.configuration
		configuration.setLocale(locale)
		configuration.setLocales(LocaleList(locale))
		resources.updateConfiguration(configuration, resources.displayMetrics)
	}
	
	/**
	 * Attaches the baseContext context to the activity, wrapping it with localization support.
	 * This method is called by the system before `onCreate()` and is crucial for applying
	 * the correct language configuration to the activity's resources. It uses [LocalizeManager.onAttach]
	 * to create a new context with the currently selected locale.
	 *
	 * @param baseContext The new baseContext context for this activity.
	 */
	override fun attachBaseContext(baseContext: Context) {
		super.attachBaseContext(LocalizeManager.onAttach(baseContext))
	}
	
	/**
	 * Called when the activity is being destroyed.
	 * This implementation unregisters the [BroadcastReceiver] to prevent memory leaks.
	 */
	public override fun onDestroy() {
		LocalizeManager.unregisterListener(this)
		super.onDestroy()
	}
	
	/**
	 * Sets the application's locale to the specified [locale].
	 *
	 * This method delegates the locale-changing logic to [LocalizeManager.setLocale],
	 * which typically involves updating the application's configuration and
	 * broadcasting an intent to notify other components of the change.
	 *
	 * @param locale The new [Locale] to be set for the application.
	 */
	override fun setLocale(locale: Locale) {
		LocalizeManager.setLocale(this, locale)
	}
	
	/**
	 * Called by the system when the device configuration changes while your activity is running.
	 * This override handles configuration changes, specifically for locale updates.
	 *
	 * When a configuration change is detected (e.g., language change), this method is triggered.
	 * It calls the superclass implementation and then schedules the activity to be recreated
	 * after a short delay. This delay ensures that the new configuration is fully applied
	 * before the UI is redrawn, preventing potential inconsistencies.
	 *
	 * @param newConfiguration The new device configuration.
	 */
	override fun onConfigurationChanged(newConfiguration: Configuration) {
		super.onConfigurationChanged(newConfiguration)
		Handler(Looper.getMainLooper())
			.postDelayed({ recreate() }, LocalizeConstant.PREPARE_DELAY)
	}
	
	/**
	 * Navigates to the language selection screen (PrepareActivity).
	 * This allows the user to change the application's locale.
	 */
	open fun openPrepareLocalize() {
		LanguagePrepareActivity.navigate(this)
	}
}