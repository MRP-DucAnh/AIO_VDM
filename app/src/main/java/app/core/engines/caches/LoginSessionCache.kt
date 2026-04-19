package app.core.engines.caches

import lib.networks.URLUtilityKT.getHostFromUrl

object LoginSessionCache {

	private val sessionMap = mutableMapOf<String, Long>()
	private const val SESSION_LIFETIME_MS: Long = 5 * 60 * 1000

	private suspend fun normalizeHost(hostUrl: String?): String? {
		if (hostUrl.isNullOrBlank()) return null
		return hostUrl.trim().lowercase()
	}

	suspend fun markUserLoggedInForHost(hostUrl: String?) {
		val key = normalizeHost(hostUrl) ?: return
		sessionMap[key] = System.currentTimeMillis()
	}

	suspend fun hasRecentLoginSessionForHost(hostUrl: String?): Boolean {
		val key = normalizeHost(hostUrl) ?: return false

		val lastLoginTs = sessionMap[key] ?: return false
		val age = System.currentTimeMillis() - lastLoginTs

		val isValid = age in 0..SESSION_LIFETIME_MS
		if (!isValid) sessionMap.remove(key)

		return isValid
	}

	suspend fun clearHost(url: String?) {
		val hostUrl = getHostFromUrl(url)
		val key = normalizeHost(hostUrl) ?: return
		sessionMap.remove(key)
	}

	suspend fun clearAll() {
		sessionMap.clear()
	}
}