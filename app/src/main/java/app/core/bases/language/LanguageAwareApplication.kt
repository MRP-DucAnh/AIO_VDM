package app.core.bases.language

import android.app.Application
import android.content.Context
import android.content.res.Configuration

/**
 * A base [Application] class that enables application-wide runtime language switching.
 *
 * This class serves as the foundation for multi-language support by integrating
 * [LocaleAwareManager] at the application level, ensuring consistent language
 * settings across all app components and throughout the app lifecycle.
 *
 * ## How Application-Level Language Support Works:
 *
 * **Early Context Interception**: By overriding `attachBaseContext()`, we can
 * apply the user's preferred locale before any app components are initialized.
 * This ensures that all activities, services, and other components start with
 * the correct language configuration from their very creation.
 *
 * **Configuration Change Resilience**: The `onConfigurationChanged()` handler
 * ensures that language settings persist through device configuration changes
 * like screen rotation, keyboard visibility changes, and night mode toggles.
 *
 * **Singleton Management**: Maintains a single instance of [LocaleAwareManager]
 * that can be accessed globally, providing consistent locale management
 * throughout the application.
 */
open class LanguageAwareApplication : Application() {

	/**
	 * Intercepts context creation to apply locale settings at the earliest possible moment.
	 *
	 * **Critical Timing**: This method is called before onCreate() and before any
	 * app components (activities, services) are initialized. By applying the locale
	 * here, we ensure that:
	 * - All activities inherit the correct locale context
	 * - Application-level resources use the proper language
	 * - The locale is set before any resource loading occurs
	 *
	 * **Initialization Sequence**:
	 * 1. Create LocaleAwareManager instance with base context
	 * 2. Apply saved locale settings to create wrapped context
	 * 3. Pass wrapped context to super for normal application initialization
	 *
	 * @param base The original application context provided by the Android system
	 */
	override fun attachBaseContext(base: Context) {
		// Initialize the locale manager before any other app components
		localeAwareManager = LocaleAwareManager(base)

		// Apply locale settings and wrap the base context
		val localeConfiguredContext = localeAwareManager?.setLocale() ?: base

		// Continue normal application initialization with locale-aware context
		super.attachBaseContext(localeConfiguredContext)
	}

	/**
	 * Handles configuration changes while maintaining language consistency.
	 *
	 * **The Problem**: When device configuration changes (rotation, night mode, etc.),
	 * Android may recreate resources and potentially revert to system default locale.
	 * This method ensures our custom locale settings are preserved and reapplied.
	 *
	 * **Common Scenarios Handled**:
	 * - Screen rotation (orientation changes)
	 * - Dark/light mode switching
	 * - Keyboard visibility changes
	 * - Display density changes
	 *
	 * **Why Reapply Locale**: Even though we set the locale in attachBaseContext,
	 * some configuration changes can cause the system to recreate resources with
	 * default locale settings. This method acts as a safeguard.
	 *
	 * @param newConfig The updated device configuration provided by the system
	 */
	override fun onConfigurationChanged(newConfig: Configuration) {
		// Reapply locale settings before handling the configuration change
		localeAwareManager?.setLocale()

		// Proceed with normal configuration change handling
		super.onConfigurationChanged(newConfig)
	}

	companion object {
		/**
		 * Global singleton instance of [LocaleAwareManager] for application-wide locale management.
		 *
		 * **Design Pattern**: Singleton pattern ensures consistent locale state across the entire app.
		 * All components (activities, fragments, services) can access the same locale manager instance.
		 *
		 * **Access Control**: Private setter restricts modification to this class only, preventing
		 * external components from accidentally replacing the manager and causing inconsistencies.
		 *
		 * **Null Safety**: The property is nullable to handle edge cases during initialization
		 * or in testing environments, but should never be null during normal app operation
		 * after attachBaseContext() completes.
		 */
		var localeAwareManager: LocaleAwareManager? = null
			private set
	}
}