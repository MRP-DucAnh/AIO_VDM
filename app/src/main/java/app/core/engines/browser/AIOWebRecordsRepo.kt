package app.core.engines.browser

import app.core.engines.browser.AIOWebRecordsRepo.getAllRecordsLazy
import app.core.engines.browser.AIOWebRecordsRepo.getAllUrls
import app.core.engines.browser.AIOWebRecordsRepo.logger
import app.core.engines.browser.AIOWebRecordsRepo.searchBookmarksFuzzy
import app.core.engines.objectbox.*
import io.objectbox.*
import io.objectbox.query.*
import io.objectbox.query.QueryBuilder.StringOrder.*
import lib.process.*

/**
 * Database access object for managing AIOWebRecords in the ObjectBox database.
 *
 * This singleton provides comprehensive CRUD operations, optimized query methods,
 * and automated maintenance utilities for the browser's web record storage. It is
 * designed for high performance, utilizing coroutine-based asynchronous execution
 * and ObjectBox's native efficiency.
 *
 * Key features include:
 * - **Memory Efficiency**: Heavy use of [LazyList] and [#findIds] to prevent heap spikes.
 * - **Advanced Search**: Multi-stage ranking algorithm using direct database matching
 * complemented by Jaro-Winkler fuzzy linguistic scoring.
 * - **Automated Maintenance**: Two-stage history cleanup (time-based and count-based).
 * - **Thread Safety**: All long-running operations are dispatched to appropriate IO contexts.
 *
 * @see AIOWebRecords The data model representing a website record, bookmark, or history item.
 * @see ObjectBoxManager The provider for the underlying database store.
 */
object AIOWebRecordsRepo {
	/** * Internal logger instance used for tracking the initialization and
	 * operational lifecycle of the [AIOWebRecords] data access layer.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/** * The default string comparison mode used for database queries.
	 * Set to [CASE_INSENSITIVE] to ensure broad match reliability across
	 * titles, URLs, and folders regardless of user input casing.
	 */
	private val defaultMatchMode = CASE_INSENSITIVE

	/** * Lazy-initialized ObjectBox [Box] for the [AIOWebRecords] entity.
	 * * This property defers database table access until the first call is made,
	 * optimizing application startup time. Upon initialization, it logs a
	 * confirmation to the [logger] to assist in debugging database availability.
	 */
	private val webRecordsBox by lazy {
		ObjectBoxManager.getBoxStore()
			.boxFor(AIOWebRecords::class.java).apply {
				logger.i("WebRecords box initialized")
			}
	}

	/**
	 * Retrieves the singleton [Box] instance for performing CRUD operations on
	 * [AIOWebRecords].
	 *
	 * @return The thread-safe ObjectBox storage interface for web records.
	 */
	@JvmStatic
	fun getWebRecordsBox(): Box<AIOWebRecords> = webRecordsBox

	/**
	 * Retrieves a memory-efficient [LazyList] of all records marked as favorites.
	 *
	 * Results are sorted by the most recently accessed time. Using a [LazyList] ensures
	 * that records are only loaded into memory when accessed by the UI, which is
	 * critical for maintaining performance when a user has a large number of favorites.
	 *
	 * @return A [LazyList] of favorite [AIOWebRecords].
	 */
	@JvmStatic
	suspend fun getFavorites(): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.equal(AIOWebRecords_.favorite, true)
				.orderDesc(AIOWebRecords_.lastAccessed)
				.build()
				.findLazy()
		}
	}

	/**
	 * Retrieves all web records within a specific folder as a [LazyList].
	 *
	 * This method performs a case-insensitive search for the specified folder name
	 * and returns the results sorted alphabetically. The lazy loading mechanism
	 * prevents memory spikes in folders containing hundreds of items.
	 *
	 * @param folder The name or path of the virtual folder to filter by.
	 * @return A [LazyList] of [AIOWebRecords] assigned to the specified folder.
	 */
	@JvmStatic
	suspend fun getRecordsInFolder(folder: String): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.equal(AIOWebRecords_.folder, folder, defaultMatchMode)
				.order(AIOWebRecords_.name)
				.build()
				.findLazy()
		}
	}

	/**
	 * Retrieves the top most frequently visited web records based on access count.
	 *
	 * Useful for "Top Sites" or "Speed Dial" features, this method sorts the
	 * entire database by popularity and returns the highest-ranking entries
	 * up to the specified limit.
	 *
	 * @param limit The maximum number of popular records to retrieve.
	 * @return A list of [AIOWebRecords] sorted by [AIOWebRecords.accessCount] descending.
	 */
	@JvmStatic
	suspend fun getMostVisited(limit: Long): List<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.orderDesc(AIOWebRecords_.accessCount)
				.build()
				.find(0, limit)
		}
	}

	/**
	 * Retrieves all records as a [LazyList] for memory-efficient iteration.
	 *
	 * This method uses ObjectBox lazy loading, which only fetches entities from the
	 * database when they are specifically accessed. This is the most efficient way
	 * to handle large datasets as it avoids loading all objects into memory at once.
	 *
	 * Note: The [LazyList] should be closed or used within a context that manages
	 * its lifecycle to prevent resource leaks.
	 *
	 * @return A [LazyList] containing all [AIOWebRecords] in the database.
	 * @sample getAllRecordsLazy().use { lazyList -> lazyList.forEach { process(it) } }
	 */
	@JvmStatic
	suspend fun getAllRecordsLazy(): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query().build().findLazy()
		}
	}

	/**
	 * Retrieves all bookmark records as a memory-efficient [LazyList].
	 *
	 * This method provides near-instantaneous results because ObjectBox [LazyList]s
	 * do not load or parse entities until they are specifically accessed. This makes
	 * it ideal for UI components like Java-based Adapters or large lists where
	 * loading all bookmarks into memory at once would be expensive.
	 *
	 * Results are sorted by priority in descending order.
	 *
	 * @return A [LazyList] of [AIOWebRecords] containing all bookmarks.
	 */
	@JvmStatic
	suspend fun getBookmarksRecordsLazy(): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.equal(AIOWebRecords_.isBookmark, true)
				.orderDesc(AIOWebRecords_.lastAccessed)
				.build()
				.findLazy()
		}
	}

	/**
	 * Retrieves all history records as a memory-efficient [LazyList].
	 *
	 * This method returns only non-bookmark entries, sorted by the most recently accessed
	 * time in descending order. Because it uses ObjectBox's [LazyList], records are
	 * loaded from the database only when accessed, making it suitable for browsing
	 * very large history datasets without high memory consumption.
	 *
	 * @return A [LazyList] of [AIOWebRecords] containing history entries (non-bookmarks).
	 */
	@JvmStatic
	suspend fun getHistoryRecordsLazy(): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.equal(AIOWebRecords_.isBookmark, false)
				.orderDesc(AIOWebRecords_.lastAccessed)
				.build()
				.findLazy()
		}
	}

	/**
	 * Retrieves the unique identifiers (IDs) for all history records.
	 *
	 * By using [Query.findIds], this method avoids the overhead of mapping database rows to
	 * JVM objects, making it the most efficient way to get a reference list of
	 * non-bookmark entries. Results are sorted by the most recently accessed time.
	 *
	 * @return A [LongArray] containing the ObjectBox IDs of all history records.
	 */
	@JvmStatic
	suspend fun getHistoryRecordsIds(): LongArray {
		return withIOContext {
			getWebRecordsBox().query()
				.equal(AIOWebRecords_.isBookmark, false)
				.orderDesc(AIOWebRecords_.lastAccessed)
				.build()
				.findIds()
		}
	}

	/**
	 * Searches for bookmarks matching a query string and returns a LazyList.
	 *
	 * Performs a case-insensitive search across bookmark titles and URLs.
	 * Results are ordered by priority descending. Because this returns a [LazyList],
	 * data is only loaded from disk as the list is iterated, making it efficient
	 * for UI adapters handling many search results.
	 *
	 * @param query The search string to match against name or URL
	 * @return A [LazyList] of matching bookmark records
	 */
	@JvmStatic
	suspend fun searchBookmarksFuzzy(query: String): List<AIOWebRecords> {
		return withIOContext {
			searchWebRecords(query, isBookmark = true)
		}
	}

	/**
	 * Performs a fuzzy search on non-bookmark history records based on name and URL.
	 *
	 * This method implements a multi-stage ranking algorithm:
	 * 1. Filters for non-bookmark records matching the query string.
	 * 2. Calculates a relevance score using exact matching, substring containment,
	 *    term-based matching, and Jaro-Winkler similarity.
	 * 3. Filters out low-relevance matches (score < 0.75).
	 * 4. Sorts results primarily by exact match status and secondarily by similarity score.
	 *
	 * The Jaro-Winkler algorithm provides a prefix-weighted similarity score, making it
	 * highly effective for finding relevant history entries even with typos or partial input.
	 *
	 * @param query The search string to match against history names or URLs.
	 * @return A list of matching [AIOWebRecords] sorted by relevance.
	 */
	@JvmStatic
	suspend fun searchHistoryFuzzy(query: String): List<AIOWebRecords> {
		return withIOContext {
			searchWebRecords(query, isBookmark = false)
		}
	}

	/**
	 * Executes a multi-stage fuzzy search to find the most relevant web records.
	 *
	 * This function follows a high-performance search pipeline:
	 * 1. **Native Filtering**: Uses ObjectBox to quickly narrow down records matching
	 * the query in the name or URL, respecting the [isBookmark] filter.
	 * 2. **Relevance Scoring**: Applies a tiered scoring system where exact matches
	 * rank highest (1.0), followed by substring matches (0.9), and term-based
	 * coverage (0.85).
	 * 3. **Linguistic Similarity**: As a fallback for typos, it calculates the
	 * Jaro-Winkler similarity to surface records that are characteristically similar.
	 * 4. **Heuristic Ranking**: Filters out results with a confidence score below 0.75
	 * and sorts the remainder by exactness and relevance.
	 *
	 * @param query The user's input string to search for.
	 * @param isBookmark Filter for bookmarks (true) or history (false).
	 * @param searchLimit The maximum number of candidates to process from the database.
	 * @return A sorted list of [AIOWebRecords] ranked by relevance.
	 */
	private suspend fun searchWebRecords(
		query: String,
		isBookmark: Boolean = false,
		searchLimit: Long = 50
	): List<AIOWebRecords> = withIOContext {
		val normalized = query.trim().lowercase()
		if (normalized.isEmpty()) return@withIOContext emptyList()

		val box = webRecordsBox
		val candidates = box.query()
			.equal(AIOWebRecords_.isBookmark, isBookmark)
			.and().apply {
				contains(AIOWebRecords_.name, normalized, defaultMatchMode)
				or()
				contains(AIOWebRecords_.url, normalized, defaultMatchMode)
			}
			.build()
			.find(0, searchLimit)

		if (candidates.isEmpty()) return@withIOContext emptyList()

		val terms = normalized.split("\\s+".toRegex()).filter { it.isNotEmpty() }

		candidates.map { record ->
			val recName = record.name.lowercase()
			val recUrl = record.url.lowercase()
			val isExact = recName == normalized || recUrl == normalized

			val score = when {
				isExact -> 1.0
				recName.contains(normalized) || recUrl.contains(normalized) -> 0.9
				terms.all { recName.contains(it) || recUrl.contains(it) } -> 0.85
				else -> {
					// Jaro-Winkler is only called if other matches fail
					maxOf(
						jaroWinklerSimilarity(normalized, recName),
						jaroWinklerSimilarity(normalized, recUrl)
					)
				}
			}
			ScoreResult(record, score, isExact)
		}
			.filter { it.score >= 0.75 }
			.sortedWith(compareByDescending<ScoreResult> {
				it.isExact
			}.thenByDescending { it.score })
			.map { it.record }
	}

	/**
	 * A lightweight internal data transfer object (DTO) used to store the
	 * intermediate ranking metadata for search results.
	 *
	 * By encapsulating the [score] and [isExact] match status alongside the
	 * [record], this class allows the ranking algorithm to perform complex
	 * sorting without multiple nested object allocations or repeated
	 * string-to-lowercase conversions.
	 *
	 * @property record The [AIOWebRecords] entity retrieved from the database.
	 * @property score The calculated relevance weight (ranging from 0.75 to 1.0).
	 * @property isExact A performance flag indicating if the query was an
	 * identical match to the record's name or URL, used as the primary sort key.
	 */
	private data class ScoreResult(
		val record: AIOWebRecords,
		val score: Double,
		val isExact: Boolean
	)

	/**
	 * Calculates the Jaro-Winkler similarity between two strings.
	 *
	 * The Jaro-Winkler distance is a measure of edit distance between two strings,
	 * where 1.0 indicates an exact match and 0.0 indicates no similarity. It is
	 * particularly effective for short strings like names and URLs, as it gives
	 * higher scores to strings that match from the beginning (prefix bonus).
	 *
	 * Used internally by [searchBookmarksFuzzy] to rank search results based on
	 * linguistic similarity when exact matches are not found.
	 *
	 * @param s1 The first string to compare.
	 * @param s2 The second string to compare.
	 * @return A similarity score between 0.0 and 1.0.
	 */
	private suspend fun jaroWinklerSimilarity(s1: String, s2: String): Double {
		return withIOContext {
			if (s1 == s2) return@withIOContext 1.0

			val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
			var matches = 0
			var transpositions = 0

			val s1Matches = BooleanArray(s1.length)
			val s2Matches = BooleanArray(s2.length)

			// Identify matching characters within the allowable distance
			for (i in s1.indices) {
				val start = maxOf(0, i - matchDistance)
				val end = minOf(i + matchDistance + 1, s2.length)

				for (j in start until end) {
					if (!s2Matches[j] && s1[i] == s2[j]) {
						s1Matches[i] = true
						s2Matches[j] = true
						matches++
						break
					}
				}
			}

			if (matches == 0) return@withIOContext 0.0

			// Count transpositions (characters matched out of order)
			var k = 0
			for (i in s1.indices) {
				if (s1Matches[i]) {
					while (!s2Matches[k]) k++
					if (s1[i] != s2[k]) transpositions++
					k++
				}
			}

			val jaro = (matches / s1.length.toDouble() +
				matches / s2.length.toDouble() +
				(matches - transpositions / 2.0) / matches) / 3.0

			// Winkler bonus for common prefix (up to 4 chars)
			val prefixLimit = minOf(4, minOf(s1.length, s2.length))
			var prefix = 0
			while (prefix < prefixLimit && s1[prefix] == s2[prefix]) {
				prefix++
			}

			return@withIOContext jaro + (prefix * 0.1 * (1 - jaro))
		}
	}

	/**
	 * Performs a standard database search for records matching the query in their name or URL.
	 *
	 * Unlike the fuzzy search variants, this method uses a direct "contains" match at the
	 * database level and sorts results strictly by popularity ([AIOWebRecords.accessCount]).
	 * This is ideal for high-performance autocomplete or "top results" suggestions.
	 *
	 * @param query The search string to look for within titles or URLs.
	 * @param limit The maximum number of matching records to return.
	 * @return A list of matching [AIOWebRecords] ordered by access frequency.
	 */
	@JvmStatic
	suspend fun searchRecords(query: String, limit: Long): List<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.contains(AIOWebRecords_.name, query, defaultMatchMode)
				.or()
				.contains(AIOWebRecords_.url, query, defaultMatchMode)
				.orderDesc(AIOWebRecords_.accessCount)
				.build()
				.find(0, limit)
		}
	}

	/**
	 * Extracts a deduplicated array of every URL stored in the web records table.
	 *
	 * This utilizes ObjectBox Property Queries with the [QueryBuilder.#DISTINCT] flag
	 * to ensure the returned array contains no repeats, significantly reducing
	 * memory footprint when generating sitemaps or validation lists.
	 *
	 * @return A unique array of all stored URL strings.
	 */
	@JvmStatic
	suspend fun getAllUrls(): Array<String> {
		return withIOContext {
			getWebRecordsBox().query().build()
				.property(AIOWebRecords_.url)
				.distinct()
				.findStrings()
		}
	}

	/**
	 * Extracts a deduplicated array of every page title stored in the database.
	 *
	 * Similar to [getAllUrls], this provides a clean list of unique site names.
	 * Useful for building search indexes or populating "Recently Visited Sites"
	 * categories in the UI.
	 *
	 * @return A unique array of all stored record names (titles).
	 */
	@JvmStatic
	suspend fun getAllTitles(): Array<String> {
		return withIOContext {
			getWebRecordsBox().query().build()
				.property(AIOWebRecords_.name)
				.distinct()
				.findStrings()
		}
	}

	/**
	 * Persists a collection of web records to the database in a single atomic transaction.
	 *
	 * This batch operation is significantly more efficient than individual puts for large
	 * datasets (such as history imports), as it minimizes disk synchronization overhead
	 * and native-to-JVM context switching.
	 *
	 * @param list The list of [AIOWebRecords] to be inserted or updated.
	 */
	@JvmStatic
	suspend fun insertRecords(list: List<AIOWebRecords>) {
		withIOContext {
			try {
				getWebRecordsBox().put(list)
				logger.d("Batch inserted ${list.size} WebRecords")
			} catch (e: Exception) {
				logger.e("Error batch inserting records", e)
			}
		}
	}

	/**
	 * Saves or updates a single web record in the database.
	 *
	 * If the record's ID is 0, ObjectBox will treat this as an insertion and assign a
	 * new unique ID. If an ID exists, the existing record will be overwritten with
	 * the provided data.
	 *
	 * @param record The [AIOWebRecords] entity to persist.
	 * @return The unique identifier (ID) assigned to the record.
	 */
	@JvmStatic
	suspend fun saveRecord(record: AIOWebRecords): Long {
		return withIOContext {
			getWebRecordsBox().put(record)
		}
	}

	/**
	 * Deletes a specific record from the database by its unique identifier.
	 *
	 * This operation is permanent and removes the record from both history and bookmarks
	 * if present. If the ID does not exist in the database, the operation completes
	 * silently without throwing an exception.
	 *
	 * @param recordId The unique ObjectBox ID of the [AIOWebRecords] entity to remove.
	 * @return true if the record was successfully removed, false otherwise.
	 */
	@JvmStatic
	suspend fun deleteRecord(recordId: Long) {
		withIOContext {
			getWebRecordsBox().remove(recordId)
		}
	}

	/**
	 * Periodically purges old or excessive history entries to maintain database health.
	 *
	 * This maintenance routine applies a dual-filter cleanup strategy:
	 * 1. **Time-based Purge**: Removes all non-bookmark records older than [olderThanDays].
	 * 2. **Size-based Purge**: If the history count still exceeds [keepCount], it identifies
	 * and removes the oldest remaining entries until the limit is met.
	 *
	 * **Efficiency Note**: This function uses `findIds` for the size-based purge to avoid
	 * the high memory cost of mapping full database entities to the JVM heap. Bookmarked
	 * records are strictly excluded from all deletion logic.
	 *
	 * @param keepCount The maximum number of non-bookmark records allowed to remain.
	 * @param olderThanDays The age threshold (in days) beyond which records are deleted.
	 */
	@JvmStatic
	suspend fun cleanupOldHistory(keepCount: Long = 50_000, olderThanDays: Long = 90) {
		withIOContext {
			runCatching {
				val box = webRecordsBox
				val threshold = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)

				// Stage 1: Date-based removal
				box.query()
					.less(AIOWebRecords_.lastAccessed, threshold)
					.equal(AIOWebRecords_.isBookmark, false)
					.build()
					.remove()

				// Stage 2: Count-based removal (Optimized with IDs)
				val currentCount = box.query().equal(AIOWebRecords_.isBookmark, false).build().count()
				if (currentCount > keepCount) {
					val excess = (currentCount - keepCount)
					// findIds is MUCH faster as it avoids object mapping
					val idsToDelete = box.query()
						.equal(AIOWebRecords_.isBookmark, false)
						.order(AIOWebRecords_.lastAccessed)
						.build()
						.findIds(0, excess)

					idsToDelete.forEach { box.remove(it) }
					logger.d("Cleanup: Removed $excess excess history IDs.")
				}
			}.onFailure { logger.e("Cleanup error", it) }
		}
	}
}