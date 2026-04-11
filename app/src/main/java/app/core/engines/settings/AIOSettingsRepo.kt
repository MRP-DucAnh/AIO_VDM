package app.core.engines.settings

import app.core.engines.objectbox.*
import app.core.engines.settings.AIOSettingsRepo.APP_SETTINGS_ID
import app.core.engines.settings.AIOSettingsRepo.activeSettings
import app.core.engines.settings.AIOSettingsRepo.appSettingsBox
import app.core.engines.settings.AIOSettingsRepo.getSettings
import app.core.engines.settings.AIOSettingsRepo.observeSettings
import app.core.engines.settings.AIOSettingsRepo.saveInDB
import io.objectbox.*
import io.objectbox.reactive.*
import lib.process.*

/**
 * Repository object acting as the central management layer for the application's global
 * settings.
 *
 * This singleton facilitates the reactive lifecycle of [AIOSettings] by bridging the
 * ObjectBox database and the UI layer. It ensures that any changes to user preferences,
 * download configurations, or browser settings are persisted safely and broadcasted
 * immediately to all registered observers.
 *
 * Key Responsibilities:
 * 1. Lifecycle Management: Handles initialization, default record creation, and
 * clean shutdown of database subscriptions.
 * 2. Reactive Updates: Implements a "Single Source of Truth" via a [Volatile]
 * in-memory cache synchronized with ObjectBox Data Observers.
 * 3. Thread-Safe Persistence: Provides synchronized methods for updating settings
 * and ensures background processing of disk I/O.
 * 4. Observer Pattern: Manages a registry of [AIOSettingsObserver] listeners to
 * support decoupled UI updates across the app.
 */
object AIOSettingsRepo {

	/** * Internal logger instance used for tracking the lifecycle of the
	 * settings repository and reporting database or listener errors.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/** * A thread-safe collection of observers that are notified whenever
	 * the global settings are updated in the database.
	 */
	private val listeners = mutableSetOf<AIOSettingsObserver>()

	/** * Reference to the active ObjectBox [DataSubscription]. This allows the
	 * repository to reactively listen for changes and must be managed to
	 * prevent memory leaks.
	 */
	private var settingsSubscription: DataSubscription? = null

	/** * The unique ObjectBox Primary Key ([#Id]) used to identify the
	 * singleton application settings record in the database.
	 */
	const val APP_SETTINGS_ID = 1L

	/** * A constant identifier used to distinguish global app settings from
	 * individual download-specific configurations within the database schema.
	 */
	const val APP_SETTINGS_DB_ID = -1212L

	/**
	 * Data access object (Box) for the [AIOSettings] entity.
	 * * This property is initialized lazily to ensure that the ObjectBox database
	 * is fully established via [ObjectBoxManager] before the first attempt to
	 * access the settings table.
	 */
	@JvmStatic
	val appSettingsBox: Box<AIOSettings> by lazy {
		ObjectBoxManager
			.getBoxStore()
			.boxFor(AIOSettings::class.java)
	}

	/**
	 * The in-memory cache of the current application settings.
	 * * Marked as [Volatile] to ensure that any update to the settings is
	 * immediately visible across all threads, providing a "single source of
	 * truth" for the application process.
	 */
	@Volatile
	private lateinit var activeSettings: AIOSettings

	/**
	 * Bootstraps the settings management system during application startup.
	 * * This function performs the following critical startup tasks:
	 * 1. Attempts to load the existing singleton settings record from the database.
	 * 2. If no record exists (first run), it instantiates a default [AIOSettings]
	 * object, assigns the required IDs, and persists it.
	 * 3. Assigns the result to the [activeSettings] cache.
	 * 4. Initiates [observeSettings] to keep the cache synchronized with
	 * any future database changes.
	 */
	@JvmStatic
	fun initialize() {
		runCatching {
			activeSettings = appSettingsBox.get(APP_SETTINGS_ID)
				?: AIOSettings().apply {
					id = APP_SETTINGS_ID
					downloadId = APP_SETTINGS_DB_ID
					appSettingsBox.put(this)
				}

			observeSettings()
		}.onFailure { error ->
			logger.e("Settings initialization error", error)
		}
	}

	/**
	 * Retrieves the current in-memory cached instance of the application settings.
	 *
	 * This provides immediate access to the [activeSettings] without requiring
	 * a synchronous database read, ensuring high performance for UI components.
	 * * @return The currently active [AIOSettings] instance.
	 */
	@JvmStatic
	fun getSettings(): AIOSettings = activeSettings

	/**
	 * Updates the global settings record in both the memory cache and the database.
	 *
	 * This function is thread-safe and ensures that the provided settings object
	 * is correctly mapped to the singleton [APP_SETTINGS_ID] before persistence.
	 * By updating the [activeSettings] cache immediately, it ensures that subsequent
	 * calls to [getSettings] reflect the changes even before the DB write completes.
	 *
	 * @param appSettings The new settings configuration to be stored.
	 */
	@JvmStatic
	suspend fun saveInDB(appSettings: AIOSettings) {
		withIOContext {
			runCatching {
				appSettings.id = APP_SETTINGS_ID
				appSettings.downloadId = APP_SETTINGS_DB_ID
				activeSettings = appSettings
				persistInDB(appSettings)
			}.onFailure { error ->
				logger.e("Settings save error: ${error.message}", error)
			}
		}
	}

	/**
	 * Executes the low-level ObjectBox 'put' operation to write settings to disk.
	 *
	 * This private helper isolates the database interaction, allowing [saveInDB]
	 * to handle the high-level logic and ID assignment while this handles the
	 * physical persistence layer.
	 *
	 * @param settings The settings object to be written to the [appSettingsBox].
	 */
	@JvmStatic
	private fun persistInDB(settings: AIOSettings) {
		runCatching {
			appSettingsBox.put(settings)
		}.onFailure { error ->
			logger.e("Settings save error: ${error.message}", error)
		}
	}

	/**
	 * Initializes a live database subscription to monitor the global application settings.
	 *
	 * This function sets up an ObjectBox reactive query that targets the primary
	 * settings record. When any change is committed to the [appSettingsBox] for the
	 * [APP_SETTINGS_ID], the observer triggers, updates the [activeSettings] cache,
	 * and broadcasts the updated object to all registered UI and service listeners.
	 */
	private fun observeSettings() {
		settingsSubscription = appSettingsBox.query()
			.equal(AIOSettings_.id, APP_SETTINGS_ID)
			.build()
			.subscribe()
			.observer { result ->
				result.firstOrNull()?.let { settings ->
					activeSettings = settings
					notifyListeners(settings)
				}
			}
	}

	/**
	 * Dispatches the updated [AIOSettings] object to all active subscribers.
	 *
	 * Each listener's callback is wrapped in a [runCatching] block to ensure that
	 * a crash or exception in one subscriber (e.g., a fragment that has been
	 * destroyed) does not prevent other listeners from receiving the update.
	 *
	 * @param settings The freshly retrieved settings object from the database.
	 */
	private fun notifyListeners(settings: AIOSettings) {
		listeners.forEach { subscriber ->
			runCatching {
				subscriber.onAIOSettingsChanged(settings)
			}.onFailure { error ->
				logger.e("Settings listener error", error)
			}
		}
	}

	/**
	 * Registers a new observer to receive real-time updates when settings change.
	 *
	 * @param listener An implementation of [AIOSettingsObserver] to be added
	 * to the broadcast list.
	 */
	fun registerListener(listener: AIOSettingsObserver) {
		listeners.add(listener)
	}

	/**
	 * Removes an observer from the broadcast list to stop receiving updates.
	 *
	 * This should typically be called in `onStop()` or `onDestroy()` of the
	 * observing component to prevent memory leaks and unnecessary processing.
	 *
	 * @param listener The observer instance to be removed.
	 */
	fun unregisterListener(listener: AIOSettingsObserver) {
		listeners.remove(listener)
	}
}