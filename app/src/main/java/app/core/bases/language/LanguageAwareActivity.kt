package app.core.bases.language

import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import app.core.bases.language.LanguageAwareApplication.Companion.localeAwareManager
import lib.process.LogHelperUtils

/**
 * An abstract base activity that enables dynamic language switching at runtime.
 *
 * This class provides the foundation for implementing in-app language changes without
 * requiring the user to leave the application. It handles the complex process of
 * updating the activity's configuration while preserving other UI settings like
 * dark/light mode.
 *
 * ## How Language Switching Works:
 *
 * **Context Wrapping**: The core mechanism involves wrapping the base context with
 * a new locale configuration. When `attachBaseContext()` is called during activity
 * creation, we intercept it and apply the user's preferred language before the
 * activity's resources are initialized.
 *
 * **Configuration Preservation**: While applying language changes, we carefully
 * preserve the current UI mode (night/day theme) to prevent unwanted theme switching
 * when only the language should change.
 *
 * **Activity Recreation**: To fully apply language changes to existing activities,
 * we recreate the activity, which triggers the entire view hierarchy to rebuild
 * with the new locale's resources.
 */
abstract class LanguageAwareActivity : AppCompatActivity() {

	/**
	 * Logger instance for debugging, tracing lifecycle events, and monitoring application behavior.
	 *
	 * This logger provides structured logging throughout the activity lifecycle, helping with
	 * debugging, performance monitoring, and issue diagnosis. It automatically uses the
	 * concrete activity class name for clear log identification.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Intercepts context attachment to apply the user's preferred locale.
	 *
	 * **Why this timing matters**: This method is called early in the activity
	 * lifecycle, before any resources are loaded. By applying the locale here,
	 * we ensure that all subsequent resource loading (layouts, strings, dimensions)
	 * uses the correct language.
	 *
	 * **Execution Flow**:
	 * 1. System calls attachBaseContext during activity initialization
	 * 2. We delegate to localeAwareManager to wrap the context with proper locale
	 * 3. If manager is unavailable, fall back to default context behavior
	 *
	 * @param context The original context provided by the Android system
	 */
	override fun attachBaseContext(context: Context) {
		val wrappedContext = localeAwareManager?.setLocale() ?: context
		super.attachBaseContext(wrappedContext)
	}

	/**
	 * Resets the activity's title to the manifest-defined value after language changes.
	 *
	 * **The Problem**: When language changes occur, activity titles set via XML
	 * may not automatically update because they're cached by the system. This method
	 * explicitly reloads the title from the AndroidManifest.xml using the current
	 * locale's string resources.
	 *
	 * **How it works**:
	 * 1. Retrieves the activity's metadata from package manager
	 * 2. Extracts the label resource ID defined in AndroidManifest.xml
	 * 3. Applies the resource ID as title, which automatically uses current locale
	 *
	 * **Error Handling**: Safely catches NameNotFoundException if activity info
	 * is unavailable, which shouldn't happen in normal operation but protects
	 * against edge cases.
	 */
	private fun resetTitle() {
		try {
			val labelRes = packageManager.getActivityInfo(componentName, GET_META_DATA).labelRes
			if (labelRes != 0) {
				setTitle(labelRes) // This automatically uses current locale's string
			}
		} catch (error: NameNotFoundException) {
			logger.e("LanguageAwareActivity - Failed to reset activity title", error)
		}
	}

	/**
	 * Applies configuration changes while preserving the current UI mode.
	 *
	 * **The Challenge**: When we override configuration for language changes,
	 * we need to ensure we don't accidentally reset other important settings
	 * like dark mode, font scale, or display size preferences.
	 *
	 * **Solution Approach**:
	 * 1. Extract the current UI mode (night/light theme) from incoming configuration
	 * 2. Apply the base context's full configuration (which includes our locale)
	 * 3. Restore the original UI mode to maintain visual theme consistency
	 *
	 * @param configuration The new configuration provided by the system,
	 *                      containing potential theme changes we want to preserve
	 */
	override fun applyOverrideConfiguration(configuration: Configuration?) {
		configuration?.let { safeConfig ->
			// Store the incoming UI mode before applying our locale configuration
			val currentUiMode = safeConfig.uiMode

			// Apply the base configuration (includes our locale settings)
			safeConfig.setTo(baseContext.resources.configuration)

			// Restore the original UI mode to maintain theme consistency
			safeConfig.uiMode = currentUiMode
		}
		super.applyOverrideConfiguration(configuration)
	}

	/**
	 * Changes the application language and refreshes the activity to apply changes.
	 *
	 * **Complete Language Switch Process**:
	 * 1. Persist the new language preference (handled by localeAwareManager)
	 * 2. Update the application's locale configuration
	 * 3. Recreate the activity to rebuild UI with new language resources
	 *
	 * **User Experience**: The recreation causes a brief visual refresh but
	 * maintains the activity stack and state, providing a seamless transition.
	 *
	 * @param language The ISO language code (e.g., "en" for English, "hi" for Hindi)
	 * @return true if the locale was successfully changed, false if the
	 *         localeAwareManager is unavailable or language change failed
	 */
	fun setNewLocale(language: String): Boolean {
		localeAwareManager?.setNewLocale(language) ?: return false
		recreate()
		return true
	}
}