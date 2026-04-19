package app.core.engines.browser

import com.google.gson.*
import kotlinx.coroutines.*
import lib.process.*
import java.net.*

/**
 * Data class representing the structure of remote bookmark JSON file.
 *
 * The JSON file contains categorized bookmarks organized by topics/categories,
 * with each category containing multiple site entries.
 *
 * Example JSON structure:
 * ```json
 * {
 *   "categories": [
 *     {
 *       "title": "Development Tools",
 *       "sites": [
 *         {"name": "GitHub", "url": "https://github.com"},
 *         {"name": "Stack Overflow", "url": "https://stackoverflow.com"}
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * @property categories List of bookmark categories
 */
data class RemoteBookmarkJson(val categories: List<Category>) {

	/**
	 * Represents a category/topic group of bookmarks.
	 *
	 * @property title Display name of the category (e.g., "News", "Development")
	 * @property sites List of sites belonging to this category
	 */
	data class Category(val title: String, val sites: List<Site>)

	/**
	 * Represents an individual bookmark entry.
	 *
	 * @property name Display title/name of the bookmark
	 * @property url Complete URL of the web page
	 */
	data class Site(val name: String, val url: String)
}

/** Logger instance for tracking bookmark fetching operations and errors */
private val logger = LogHelperUtils.from(RemoteBookmarkJson::class.java)

/** Default URL for the remote bookmark JSON file hosted on GitHub */
private const val REMOTE_BOOKMARKS_URL =
	"https://raw.githubusercontent.com/shibaFoss/ytder/refs/heads/main/bookmark_sites.json"

/**
 * Fetches and parses bookmark data from a remote JSON file.
 *
 * This function downloads a JSON file containing categorized bookmarks,
 * parses it, and converts each entry into an AIOWebRecords object ready
 * for database storage.
 *
 * **Process:**
 * 1. Downloads JSON content from the specified URL
 * 2. Parses JSON into RemoteBookmarkJson data class
 * 3. Iterates through categories and sites
 * 4. Creates AIOWebRecords for each site with isBookmark = true
 *
 * **Error Handling:**
 * - Network errors: Returns empty list and logs error
 * - JSON parsing errors: Returns empty list and logs error
 * - Malformed URL: Throws MalformedURLException (caught and logged)
 *
 * @param remoteUrl URL of the bookmark JSON file (defaults to GitHub raw URL)
 * @return List of AIOWebRecords created from the JSON data, or empty list on error
 *
 * @sample
 * // Use default bookmark source
 * val bookmarks = fetchBookmarksFromJson()
 *
 * // Use custom JSON source
 * val customBookmarks = fetchBookmarksFromJson("https://example.com/bookmarks.json")
 *
 * @see RemoteBookmarkJson For the expected JSON structure
 * @see AIOWebRecords For the database entity structure
 */
private suspend fun fetchBookmarksFromJson(remoteUrl: String = REMOTE_BOOKMARKS_URL):
	List<AIOWebRecords> = withContext(Dispatchers.IO) {
	try {
		// Download JSON content from remote URL
		val jsonText = URL(remoteUrl).readText()
		val gson = Gson()
		val remoteSites = gson.fromJson(jsonText, RemoteBookmarkJson::class.java)

		// Convert JSON data to AIOWebRecords objects
		val bookmarks = ArrayList<AIOWebRecords>()
		for (category in remoteSites.categories) {
			for (site in category.sites) {
				bookmarks.add(
					AIOWebRecords(
						name = site.name,
						url = site.url,
						isBookmark = true,  // Mark as bookmark, not history entry
					)
				)
			}
		}
		bookmarks
	} catch (error: Exception) {
		logger.e("Error fetching bookmarks: ${error.message}", error)
		emptyList()
	}
}

/**
 * Populates the database with initial bookmarks if no records exist.
 *
 * This function checks if the database is empty and, if so, fetches the
 * default bookmark set from the remote JSON source and stores them in
 * the ObjectBox database.
 *
 * **Use Cases:**
 * - First-time app initialization
- Resetting user data to default state
 * - Testing environments requiring baseline data
 *
 * **Behavior:**
 * - Only adds bookmarks if database has zero records (using lazy-loaded check)
 * - Fetches bookmarks asynchronously on IO thread
 * - Each bookmark is individually inserted into the database
 * - Existing records are never modified or deleted
 *
 * **Performance Note:**
 * Uses getAllRecordsLazy().isEmpty() for efficient zero-record check
 * without loading all records into memory.
 *
 * **Idempotency:**
 * This function is idempotent - calling it multiple times only inserts
 * bookmarks once when the database is first empty.
 *
 * @see fetchBookmarksFromJson For the bookmark fetching logic
 * @see AIOWebRecordsRepo.getWebRecordsBox For database access
 *
 * @sample
 * // Call during app startup
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         GlobalScope.launch {
 *             populateInitialBookmarks()
 *         }
 *     }
 * }
 */
suspend fun syncDefaultBookmarks() {
	withContext(Dispatchers.IO) {
		// Only populate if database is completely empty
		if (AIOWebRecordsRepo.getAllRecordsLazy().isEmpty()) {
			fetchBookmarksFromJson().let { detailedBookmarks ->
				// Insert each bookmark individually into the database
				detailedBookmarks.forEach { item ->
					AIOWebRecordsRepo.getWebRecordsBox().put(item)
				}
			}
		}
	}
}