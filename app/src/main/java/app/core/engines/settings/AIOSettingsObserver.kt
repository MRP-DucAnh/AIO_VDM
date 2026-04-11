package app.core.engines.settings

/**
 * Interface definition for a callback to be invoked when AIO application settings are modified.
 *
 * Implement this interface in any component (Activity, Fragment, or Service) that needs
 * to react dynamically to configuration changes such as:
 * - Storage path migrations.
 * - Network speed limit adjustments.
 * - UI theme switching (Light/Dark mode).
 * - Parallel connection limit updates.
 *
 * @author Shiba
 */
interface AIOSettingsObserver {

	/**
	 * Triggered when a change is detected in the global [AIOSettings].
	 *
	 * This method provides the updated settings object, allowing the observer
	 * to re-initialize its internal state or refresh its UI accordingly.
	 *
	 * @param settings The fresh [AIOSettings] instance containing the latest user configurations.
	 */
	fun onAIOSettingsChanged(settings: AIOSettings)
}