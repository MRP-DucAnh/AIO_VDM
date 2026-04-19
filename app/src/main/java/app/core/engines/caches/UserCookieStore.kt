package app.core.engines.caches

import android.content.*
import kotlinx.coroutines.sync.*
import lib.networks.URLUtilityKT.getBaseDomain
import lib.process.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

object UserCookieStore {

	private const val MAX_PARALLEL_OPS = 1000

	private var cookieDir: File? = null
	private val domainLocks = ConcurrentHashMap<String, LockEntry>()
	private val globalGate = Semaphore(MAX_PARALLEL_OPS)

	private val memoryCache: MutableMap<String, String> =
		Collections.synchronizedMap(
			object : LinkedHashMap<String, String>(16, 0.75f, true) {
				override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean {
					return size > 100
				}
			}
		)

	private class LockEntry {
		val mutex = Mutex()
		val activeCount = AtomicInteger(0)
	}

	fun initialize(context: Context) {
		val dir = File(context.filesDir, "cookies")
		if (!dir.exists()) dir.mkdirs()
		cookieDir = dir
	}

	private suspend fun <T> withDomainLock(host: String, block: suspend () -> T): T {
		val entry = domainLocks.compute(host) { _, existing ->
			(existing ?: LockEntry()).apply { activeCount.incrementAndGet() }
		}!!

		return try {
			entry.mutex.withLock { block() }
		} finally {
			domainLocks.computeIfPresent(host) { _, current ->
				if (current.activeCount.decrementAndGet() == 0)
					null else current
			}
		}
	}

	private suspend fun <T> withGlobalAccess(block: suspend () -> T): T {
		globalGate.acquire()
		return try {
			block()
		} finally {
			globalGate.release()
		}
	}

	@JvmStatic
	suspend fun saveCookie(urlOrHost: String, cookie: String) {
		withIOContext {
			val host = extractHost(urlOrHost) ?: return@withIOContext
			val cleanCookie = cookie.trim()
			val dir = cookieDir ?: return@withIOContext

			withGlobalAccess {
				withDomainLock(host) {
					if (memoryCache[host] == cleanCookie) return@withDomainLock
					val file = cookieFile(dir, host)
					val tempFile = File(file.absolutePath + ".tmp")

					try {
						tempFile.writeText(cleanCookie)
						if (!tempFile.renameTo(file)) {
							if (file.exists()) file.delete()
							if (!tempFile.renameTo(file)) throw IOException()
						}

						memoryCache[host] = cleanCookie
					} catch (_: Exception) {
						if (tempFile.exists()) tempFile.delete()
					}
				}
			}
		}
	}

	@JvmStatic
	suspend fun loadCookie(urlOrHost: String): String? {
		return withIOContext {
			val host = extractHost(urlOrHost) ?: return@withIOContext null
			val dir = cookieDir ?: return@withIOContext null

			withGlobalAccess {
				withDomainLock(host) {
					memoryCache[host]?.let { return@withDomainLock it }
					val file = cookieFile(dir, host)
					if (file.exists()) {
						val content = try {
							file.readText().trim()
						} catch (_: Exception) {
							null
						}

						if (content != null) memoryCache[host] = content
						content
					} else null
				}
			}
		}
	}

	suspend fun peekCookie(urlOrHost: String): String? {
		val host = runCatching { getBaseDomain(urlOrHost) }.getOrNull()
			?: return null
		return memoryCache[host]
	}

	@JvmStatic
	suspend fun clearCookie(urlOrHost: String): String? {
		return withIOContext {
			val host = extractHost(urlOrHost) ?: return@withIOContext null
			val dir = cookieDir ?: return@withIOContext null

			withGlobalAccess {
				withDomainLock(host) {
					val file = cookieFile(dir, host)
					if (file.exists()) file.delete()
					memoryCache.remove(host)
				}
			}
		}
	}

	@JvmStatic
	suspend fun clearAll() {
		withIOContext {
			val dir = cookieDir ?: return@withIOContext
			repeat(MAX_PARALLEL_OPS) { globalGate.acquire() }

			try {
				memoryCache.clear()
				domainLocks.clear()
				dir.listFiles()?.forEach { it.delete() }
			} finally {
				repeat(MAX_PARALLEL_OPS) { globalGate.release() }
			}
		}
	}

	private suspend fun cookieFile(dir: File, host: String): File {
		val safeHost = host.lowercase().replace("[^a-z0-9._-]".toRegex(), "_")
		return File(dir, "$safeHost.txt")
	}

	private suspend fun extractHost(urlOrHost: String): String? {
		return runCatching { getBaseDomain(urlOrHost) }.getOrNull()
	}
}