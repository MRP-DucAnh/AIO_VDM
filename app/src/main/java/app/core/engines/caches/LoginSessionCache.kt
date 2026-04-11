package app.core.engines.caches

import app.core.engines.caches.LoginSessionCache.SESSION_LIFETIME_MS
import lib.networks.URLUtilityKT.getHostFromUrl
import lib.process.LogHelperUtils

/**
 * An in-memory cache that tracks recent successful login sessions for specific host URLs.
 *
 * This cache is used to avoid redundant login prompts within a short time window ([SESSION_LIFETIME_MS]).
 * It provides methods to record a login, check if a session is still valid, and clear the cache.
 * All entries are automatically expired and removed once they exceed the defined lifetime.
 */
object LoginSessionCache {

	/**
	 * Logger instance for tracking login session activity and cache operations.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Internal storage mapping normalized host URLs to their last successful login timestamp (in milliseconds).
	 *
	 * This map is used to determine if a user session is still valid based on the defined
	 * session lifetime constant.
	 */
	private val sessionMap = mutableMapOf<String, Long>()

	/**
	 * The duration, in milliseconds, for which a login session is considered valid.
	 * Currently set to 5 minutes (5 * 60 * 1000).
	 */
	private const val SESSION_LIFETIME_MS: Long = 5 * 60 * 1000

	/**
	 * Normalizes a host URL by trimming whitespace and converting it to lowercase.
	 * This ensures consistency when using the URL as a key in the session map.
	 *
	 * @param hostUrl The raw host URL string to normalize.
	 * @return The normalized string, or `null` if the input is null or blank.
	 */
	private fun normalizeHost(hostUrl: String?): String? {
		if (hostUrl.isNullOrBlank()) return null
		return hostUrl.trim().lowercase()
	}

	/**
	 * Records a successful login timestamp for the specified host.
	 *
	 * This method normalizes the [hostUrl] and updates the internal cache with the current
	 * system time, effectively starting the countdown for the session's lifetime.
	 *
	 * @param hostUrl The URL of the host where the user logged in. If null or blank, no action is taken.
	 */
	fun markUserLoggedInForHost(hostUrl: String?) {
		val key = normalizeHost(hostUrl) ?: return
		val now = System.currentTimeMillis()
		sessionMap[key] = now
		logger.i("User logged in for host=$key at ts=$now")
	}

	/**
	 * Checks if there is a valid, non-expired login session for the specified host.
	 *
	 * This method normalizes the [hostUrl] and compares the stored login timestamp
	 * against the current system time. If the session has exceeded [SESSION_LIFETIME_MS],
	 * it is automatically removed from the cache.
	 *
	 * @param hostUrl The URL of the host to check.
	 * @return `true` if a valid session exists within the allowed lifetime, `false` otherwise.
	 */
	fun hasRecentLoginSessionForHost(hostUrl: String?): Boolean {
		logger.d("Checking for recent login session for host=$hostUrl")
		val key = normalizeHost(hostUrl) ?: return false

		val lastLoginTs = sessionMap[key] ?: return false
		val now = System.currentTimeMillis()
		val age = now - lastLoginTs

		val isValid = age in 0..SESSION_LIFETIME_MS

		// Automatically clean up expired entries
		if (!isValid) {
			sessionMap.remove(key)
			logger.i("Login session expired and removed for host=$key (ageMs=$age)")
		}

		return isValid
	}

	/**
	 * Clears all cached login sessions from the in-memory map.
	 *
	 * Use this method to reset the cache state, typically during a global logout
	 * or when the user's session data needs to be invalidated across all sites.
	 */
	fun clearHost(url: String?) {
		val hostUrl = getHostFromUrl(url)
		if (hostUrl.isNullOrEmpty()) return
		val key = normalizeHost(hostUrl) ?: return

		if (sessionMap.remove(key) != null) {
			logger.i("Login session cleared from cache for host=$key")
		}
	}

	/**
	 * Clears all cached login sessions from the in-memory map.
	 *
	 * Use this method to reset the cache state, typically during a global logout
	 * or when the user's session data needs to be invalidated across all sites.
	 */
	fun clearAll() {
		sessionMap.clear()
		logger.i("All site login sessions cleared from cache")
	}
}