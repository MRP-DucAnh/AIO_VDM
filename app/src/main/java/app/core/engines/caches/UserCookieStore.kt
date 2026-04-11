package app.core.engines.caches

import android.content.Context
import app.core.engines.caches.UserCookieStore.MAX_PARALLEL_OPS
import app.core.engines.caches.UserCookieStore.clearAll
import app.core.engines.caches.UserCookieStore.cookieDir
import app.core.engines.caches.UserCookieStore.domainLocks
import app.core.engines.caches.UserCookieStore.init
import app.core.engines.caches.UserCookieStore.withDomainLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lib.networks.URLUtilityKT
import lib.process.LogHelperUtils
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hardened persistent cookie storage with per-domain concurrency serialization.
 *
 * Concurrency & Safety Guarantees:
 * - **Domain Serialization**: Uses a wait-list (Mutex) per host. Only one write/read per domain occurs at a time.
 * - **Global Coordination**: Uses a large-permit Semaphore to allow parallel domain operations while
 *   ensuring `clearAll()` has exclusive access (blocking new ops and waiting for in-flight ones).
 * - **Lock Safety**: Lock lifecycle is managed via `AtomicInteger` inside `ConcurrentHashMap.compute`
 *   to prevent leaks and race conditions during entry removal.
 * - **Atomic Writes**: Implements "Write-Rename" pattern. Prevents corruption if the process is killed during IO.
 * - **Bounded Cache**: LRU memory cache limited to 100 entries to prevent memory exhaustion over long uptimes.
 */
object UserCookieStore {

	/** Logger for diagnostic messages and error reporting. */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Internal reference to the directory where cookie files are persisted.
	 * Initialized via [init] using the application's internal storage.
	 */
	private var cookieDir: File? = null

	/**
	 * Bounded Least Recently Used (LRU) cache used to deduplicate disk writes and accelerate lookups.
	 *
	 * Capacity is limited to 100 domains to balance performance with memory consumption.
	 * Wrapped in a synchronized wrapper to ensure thread-safe access across concurrent domain operations.
	 */
	private val memoryCache: MutableMap<String, String> = Collections.synchronizedMap(
		object : LinkedHashMap<String, String>(16, 0.75f, true) {
			override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean = size > 100
		}
	)

	/**
	 * Map of active domain-specific locks.
	 * Entries are managed atomically via [withDomainLock] to ensure [LockEntry]
	 * objects are created when needed and removed when no longer in use.
	 */
	private val domainLocks = ConcurrentHashMap<String, LockEntry>()

	/**
	 * Maximum number of concurrent domain-level operations allowed.
	 * This value acts as the total permit count for the global semaphore:
	 * - Individual domain operations (read/write) acquire 1 permit.
	 * - [clearAll] acquires all permits to ensure exclusive access.
	 */
	private const val MAX_PARALLEL_OPS = 1000

	/**
	 * Coordinates access between granular domain operations and destructive global operations.
	 * Regular operations (get/save) acquire 1 permit, while [clearAll] acquires all
	 * [MAX_PARALLEL_OPS] permits to ensure exclusive access.
	 */
	private val globalGate = Semaphore(MAX_PARALLEL_OPS)

	/**
	 * Container for a domain-specific synchronization primitive.
	 *
	 * Tracks the number of active operations (readers/writers) for a specific host
	 * to facilitate safe removal from [domainLocks] once the lock is no longer needed,
	 * preventing memory leaks of unused [Mutex] objects.
	 *
	 * @property mutex The mutual exclusion lock for serializing domain access.
	 * @property activeCount The number of coroutines currently holding or waiting for this lock.
	 */
	private class LockEntry {
		val mutex = Mutex()
		val activeCount = AtomicInteger(0)
	}

	/**
	 * Initializes the persistent cookie storage directory.
	 *
	 * This method sets up the "cookies" subdirectory within the application's internal files
	 * directory. It must be called before any read or write operations are performed to
	 * ensure [cookieDir] is correctly resolved.
	 *
	 * @param context The application context used to locate the internal storage directory.
	 */
	fun init(context: Context) {
		val dir = File(context.filesDir, "cookies")
		if (!dir.exists() && !dir.mkdirs()) {
			logger.e("Failed to create cookie directory: ${dir.absolutePath}")
		}
		cookieDir = dir
	}

	/**
	 * Executes a given [block] within a domain-specific lock to ensure serialized access for a host.
	 *
	 * This method manages a pool of [Mutex] objects stored in [domainLocks]. It uses an
	 * atomic reference counting mechanism via [LockEntry.activeCount] to ensure that:
	 * 1. Multiple requests for the same [host] wait on the same [Mutex].
	 * 2. The [LockEntry] is created on-demand and removed from the map once the last
	 *    waiting operation for that domain completes, preventing memory leaks.
	 *
	 * @param host The normalized domain or hostname used as the lock key.
	 * @param block The suspendable operation to perform while holding the domain lock.
	 * @return The result of the [block].
	 */
	private suspend fun <T> withDomainLock(host: String, block: suspend () -> T): T {
		// Increment usage count or create a new lock entry atomically within the map
		val entry = domainLocks.compute(host) { _, existing ->
			(existing ?: LockEntry()).apply { activeCount.incrementAndGet() }
		}!!

		return try {
			entry.mutex.withLock { block() }
		} finally {
			// Decrement usage count and remove the lock entry if no one else is waiting
			domainLocks.computeIfPresent(host) { _, current ->
				if (current.activeCount.decrementAndGet() == 0) null else current
			}
		}
	}

	/**
	 * Executes a block of code after acquiring a permit from the global gate.
	 *
	 * This mechanism ensures that individual domain-level operations can run in parallel
	 * while allowing [clearAll] to gain exclusive access by exhausting all available permits.
	 *
	 * @param T The return type of the block.
	 * @param block The suspendable operation to perform within the global gate.
	 * @return The result of the operation.
	 */
	private suspend fun <T> withGlobalAccess(block: suspend () -> T): T {
		globalGate.acquire()
		return try {
			block()
		} finally {
			globalGate.release()
		}
	}

	/**
	 * Persists a cookie string for a specific domain or URL with atomicity guarantees.
	 *
	 * This operation performs the following steps:
	 * 1. Normalizes the input to a base domain.
	 * 2. Acquires a global concurrency permit and a domain-specific lock.
	 * 3. Checks the memory cache to skip redundant writes.
	 * 4. Performs an atomic "Write-Rename" to disk to prevent file corruption.
	 * 5. Updates the LRU memory cache upon success.
	 *
	 * @param urlOrHost The full URL or raw hostname to associate the cookie with.
	 * @param cookie The raw cookie string to be stored.
	 */
	@JvmStatic
	suspend fun saveCookie(urlOrHost: String, cookie: String) = withContext(Dispatchers.IO) {
		val host = extractHost(urlOrHost) ?: return@withContext
		val cleanCookie = cookie.trim()
		val dir = cookieDir ?: return@withContext logger.e("UserCookieStore not initialized")

		withGlobalAccess {
			withDomainLock(host) {
				// Deduplication: Skip write if content is identical to cache
				if (memoryCache[host] == cleanCookie) return@withDomainLock

				val file = cookieFile(dir, host)
				val tempFile = File(file.absolutePath + ".tmp")

				try {
					// Write to temp file
					tempFile.writeText(cleanCookie)

					// Atomic Rename: Standard Android/Linux behavior is to overwrite
					if (!tempFile.renameTo(file)) {
						// Fallback if rename fails (some scoped storage edge cases)
						if (file.exists() && !file.delete()) {
							throw IOException("Could not delete existing cookie file for $host")
						}
						if (!tempFile.renameTo(file)) {
							throw IOException("Atomic rename failed for $host")
						}
					}

					memoryCache[host] = cleanCookie
					logger.d("Cookie saved for domain: $host")
				} catch (e: Exception) {
					if (tempFile.exists()) tempFile.delete()
					logger.e("Failed to save cookie for $host: ${e.message}")
				}
			}
		}
	}

	/**
	 * Load cookies for a given URL or hostname.
	 *
	 * This operation performs an O(1) lookup in the memory cache first. If not found,
	 * it performs a thread-safe disk read synchronized by domain-specific locks to
	 * ensure consistency.
	 *
	 * @param urlOrHost The full URL or raw hostname to look up.
	 * @return The raw Cookie string if found, or null if the store is uninitialized,
	 *         the domain is invalid, or no cookies exist for the host.
	 */
	@JvmStatic
	suspend fun loadCookie(urlOrHost: String): String? = withContext(Dispatchers.IO) {
		val host = extractHost(urlOrHost) ?: return@withContext null
		val dir = cookieDir ?: return@withContext null

		withGlobalAccess {
			withDomainLock(host) {
				// Check memory cache first (O(1) lookup)
				memoryCache[host]?.let { return@withDomainLock it }

				val file = cookieFile(dir, host)
				if (file.exists()) {
					val content = try {
						file.readText().trim()
					} catch (e: Exception) {
						logger.e("Read error for $host: ${e.message}")
						null
					}

					if (content != null) {
						memoryCache[host] = content
					}
					content
				} else {
					null
				}
			}
		}
	}

	/**
	 * Synchronously checks for the existence of cookies in the in-memory cache without
	 * performing disk I/O or acquiring locks.
	 *
	 * This provides a "fast-path" check to see if a domain's cookies are already loaded.
	 * Note that a null return does not guarantee that cookies do not exist on disk;
	 * it only indicates they are not currently present in the LRU memory cache.
	 *
	 * @param urlOrHost The full URL or raw hostname to look up in the cache.
	 * @return The raw Cookie string if present in memory, or null if not cached or
	 *         the domain is invalid.
	 */
	fun peekCookie(urlOrHost: String): String? {
		val host = runCatching { URLUtilityKT.getBaseDomain(urlOrHost) }.getOrNull()
			?: return null
		return memoryCache[host]
	}


	/**
	 * Clear cookies for a specific URL or hostname.
	 *
	 * This operation removes the cookie data from both the persistent storage (disk)
	 * and the in-memory cache. Access is synchronized per-domain to ensure
	 * consistency with concurrent read/write operations.
	 *
	 * @param urlOrHost The full URL or raw hostname for which cookies should be cleared.
	 */
	@JvmStatic
	suspend fun clearCookie(urlOrHost: String) = withContext(Dispatchers.IO) {
		val host = extractHost(urlOrHost) ?: return@withContext
		val dir = cookieDir ?: return@withContext

		withGlobalAccess {
			withDomainLock(host) {
				val file = cookieFile(dir, host)
				if (file.exists() && !file.delete()) {
					logger.e("Failed to delete cookie file for $host")
				}
				memoryCache.remove(host)
			}
		}
	}

	/**
	 * Clears all stored cookies from memory and persistent storage.
	 *
	 * This is an exclusive operation that:
	 * 1. Blocks all new domain-specific read/write operations.
	 * 2. Waits for all currently in-flight operations to complete by acquiring all global permits.
	 * 3. Purges the LRU memory cache and the domain lock registry.
	 * 4. Deletes all cookie files from the internal storage directory.
	 *
	 * Once complete, all permits are released to resume normal operations.
	 */
	@JvmStatic
	suspend fun clearAll() = withContext(Dispatchers.IO) {
		val dir = cookieDir ?: return@withContext

		// Block all new operations and wait for current ones to finish
		repeat(MAX_PARALLEL_OPS) { globalGate.acquire() }

		try {
			memoryCache.clear()
			domainLocks.clear()
			dir.listFiles()?.forEach { it.delete() }
			logger.d("All cookies cleared successfully")
		} finally {
			// Restore access
			repeat(MAX_PARALLEL_OPS) { globalGate.release() }
		}
	}

	/**
	 * Resolves a [File] reference for a specific host within the cookie directory.
	 * Normalizes the hostname to a safe filename format (lowercase alphanumeric, dots, underscores, and hyphens).
	 *
	 * @param dir The directory where cookie files are stored.
	 * @param host The raw hostname or domain to be sanitized.
	 * @return A [File] object representing the persistent storage location for the host's cookies.
	 */
	private fun cookieFile(dir: File, host: String): File {
		val safeHost = host.lowercase().replace("[^a-z0-9._-]".toRegex(), "_")
		return File(dir, "$safeHost.txt")
	}

	/**
	 * Extracts the normalized host/domain from a given URL or raw host string.
	 *
	 * @param urlOrHost The full URL or host string to process.
	 * @return The base domain string if parsing is successful, or null if the input is invalid.
	 */
	private fun extractHost(urlOrHost: String): String? {
		return try {
			URLUtilityKT.getBaseDomain(urlOrHost)
		} catch (error: Exception) {
			logger.e("Invalid URL: $urlOrHost", error)
			null
		}
	}
}
