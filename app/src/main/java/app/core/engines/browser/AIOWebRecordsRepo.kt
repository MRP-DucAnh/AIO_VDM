package app.core.engines.browser

import app.core.engines.objectbox.*
import io.objectbox.*
import io.objectbox.query.*
import io.objectbox.query.QueryBuilder.StringOrder.*
import lib.process.*
import java.lang.System.*

object AIOWebRecordsRepo {

	private val logger = LogHelperUtils.from(javaClass)

	private val defaultMatchMode = CASE_INSENSITIVE

	private val webRecordsBox by lazy {
		ObjectBoxManager.getBoxStore()
			.boxFor(AIOWebRecords::class.java).apply {
				logger.i("WebRecords box initialized")
			}
	}

	@JvmStatic
	fun getWebRecordsBox(): Box<AIOWebRecords> = webRecordsBox

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

	@JvmStatic
	suspend fun getMostVisited(limit: Long): List<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query()
				.orderDesc(AIOWebRecords_.accessCount)
				.build()
				.find(0, limit)
		}
	}

	@JvmStatic
	suspend fun getAllRecordsLazy(): LazyList<AIOWebRecords> {
		return withIOContext {
			getWebRecordsBox().query().build().findLazy()
		}
	}

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

	@JvmStatic
	suspend fun searchBookmarksFuzzy(query: String): List<AIOWebRecords> {
		return withIOContext {
			searchWebRecords(query, isBookmark = true)
		}
	}

	@JvmStatic
	suspend fun searchHistoryFuzzy(query: String): List<AIOWebRecords> {
		return withIOContext {
			searchWebRecords(query, isBookmark = false)
		}
	}

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

	private data class ScoreResult(
		val record: AIOWebRecords,
		val score: Double,
		val isExact: Boolean
	)

	private suspend fun jaroWinklerSimilarity(s1: String, s2: String): Double {
		return withIOContext {
			if (s1 == s2) return@withIOContext 1.0

			val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
			var matches = 0
			var transpositions = 0

			val s1Matches = BooleanArray(s1.length)
			val s2Matches = BooleanArray(s2.length)

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

			val prefixLimit = minOf(4, minOf(s1.length, s2.length))
			var prefix = 0
			while (prefix < prefixLimit && s1[prefix] == s2[prefix]) {
				prefix++
			}

			return@withIOContext jaro + (prefix * 0.1 * (1 - jaro))
		}
	}

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

	@JvmStatic
	suspend fun getAllUrls(): Array<String> {
		return withIOContext {
			getWebRecordsBox().query().build()
				.property(AIOWebRecords_.url)
				.distinct()
				.findStrings()
		}
	}

	@JvmStatic
	suspend fun getAllTitles(): Array<String> {
		return withIOContext {
			getWebRecordsBox().query().build()
				.property(AIOWebRecords_.name)
				.distinct()
				.findStrings()
		}
	}

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

	@JvmStatic
	suspend fun saveRecord(record: AIOWebRecords): Long {
		return withIOContext {
			getWebRecordsBox().put(record)
		}
	}

	@JvmStatic
	suspend fun deleteRecord(recordId: Long) {
		withIOContext {
			getWebRecordsBox().remove(recordId)
		}
	}

	@JvmStatic
	suspend fun cleanupOldHistory(keepCount: Long = 50_000, olderThanDays: Long = 90) {
		withIOContext {
			runCatching {
				val box = webRecordsBox
				val currentTimeMillis = olderThanDays * 24 * 60 * 60 * 1000L
				val threshold = currentTimeMillis() - currentTimeMillis

				box.query()
					.less(AIOWebRecords_.lastAccessed, threshold)
					.equal(AIOWebRecords_.isBookmark, false)
					.build()
					.remove()

				val currentCount = box.query()
					.equal(AIOWebRecords_.isBookmark, false)
					.build().count()

				if (currentCount > keepCount) {
					val excess = (currentCount - keepCount)
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