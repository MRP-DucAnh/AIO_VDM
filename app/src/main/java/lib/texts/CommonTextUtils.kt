package lib.texts

import android.text.*
import android.text.Html.*
import androidx.annotation.*
import app.core.AIOApp.Companion.INSTANCE
import lib.process.*
import lib.process.LocalizationHelper.getLocalizedString
import lib.texts.CommonTextUtils.convertRawHtmlFileToString
import lib.texts.CommonTextUtils.isValidCharacter
import lib.texts.CommonTextUtils.logger
import java.io.*
import java.util.Locale.*

/**
 * A comprehensive utility object providing high-level string manipulation,
 * formatting, and resource processing capabilities.
 *
 * This utility acts as a central hub for text-related logic, encompassing everything
 * from basic string transformations (reversal, capitalization) to complex
 * Android-specific tasks like HTML parsing and resource file reading.
 *
 * ### Architecture
 * * **Thread Safety:** Most methods are `suspend` functions wrapped in `withIOContext`
 * to ensure that heavy string processing and I/O operations do not block the Main thread.
 * * **Logging:** Utilizes a localized [logger] instance to track errors and
 * debug transformations across the application.
 * * **Unicode Awareness:** Includes safety measures for handling multibyte characters
 * to prevent text corruption during truncation.
 */
object CommonTextUtils {

	/**
	 * Internal logger instance used for tracking text processing errors,
	 * resource failures, and debugging random string generation.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Cleans a string by replacing multiple consecutive forward slashes with a single slash.
	 *
	 * This is particularly useful for normalizing file paths or URLs where redundant
	 * separators may have been introduced during string concatenation.
	 *
	 * ### Example:
	 * `normalizePath("my//folder///file.txt")` returns `"my/folder/file.txt"`
	 *
	 * @param input The path or string to normalize.
	 * @return A string with collapsed slashes, or null if the input was null.
	 */
	@JvmStatic
	fun removeDuplicateSlashes(input: String?): String? {
		if (input == null) return null
		// Uses a regular expression to find occurrences of 2 or more slashes
		val result = input.replace("/{2,}".toRegex(), "/")
		return result
	}

	/**
	 * Retrieves a localized string from the application resources.
	 *
	 * This method acts as a static bridge to [getLocalizedString], ensuring that the
	 * correct language/locale string is fetched based on the current app configuration.
	 *
	 * @param resID The Android string resource ID (e.g., R.string.app_name).
	 * @return The localized string content.
	 */
	@JvmStatic
	fun getText(@StringRes resID: Int): String {
		return getLocalizedString(INSTANCE, resID)
	}

	/**
	 * Generates a cryptographically insecure random alphanumeric string of a specified length.
	 *
	 * This method builds a string using a character set containing uppercase letters,
	 * lowercase letters, and digits (0-9). The operation is offloaded to [withIOContext]
	 * to ensure that long string generation or repeated calls do not impact UI fluidness.
	 *
	 * ### Performance Note:
	 * Uses [StringBuilder] for efficient character appending and logs the result
	 * for debugging purposes via [logger].
	 *
	 * @param length The desired number of characters in the generated string.
	 * @return A random alphanumeric string.
	 */
	@JvmStatic
	suspend fun generateRandomString(length: Int): String {
		return withIOContext {
			val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
			val sb = StringBuilder(length)
			for (index in 0 until length) {
				val randomIndex = (characters.indices).random()
				sb.append(characters[randomIndex])
			}
			val result = sb.toString()
			logger.d("generateRandomString: length=$length result='$result'")
			return@withIOContext result
		}
	}

	/**
	 * Safely truncates a string to a specified length while respecting Unicode code points
	 * and ensuring the result does not end on an invalid or whitespace character.
	 *
	 * Unlike standard `substring`, this method uses [codePointCount] and [offsetByCodePoints]
	 * to ensure that multibyte characters (like Emojis or specific Asian characters) are not
	 * sliced in half, which would result in "broken" replacement characters ().
	 *
	 * ### Post-processing:
	 * After cutting, the method iterates backward to remove trailing whitespace or characters
	 * deemed invalid by [isValidCharacter] (e.g., punctuation or symbols not in the allowed set).
	 *
	 * @param input The source string to be truncated.
	 * @param maxLength The maximum number of Unicode code points allowed. Defaults to 60.
	 * @return A safely truncated string, or null if the input was null.
	 */
	@JvmStatic
	suspend fun safeCutString(input: String?, maxLength: Int = 60): String? {
		return withIOContext {
			if (input == null) return@withIOContext null

			val codePointCount = input.codePointCount(0, input.length)
			if (codePointCount <= maxLength) return@withIOContext input

			val safeLength = minOf(maxLength, codePointCount)
			val endIndex = input.offsetByCodePoints(0, safeLength)
			var result = input.take(endIndex)

			// Clean up the tail of the string for better UI presentation
			while (result.isNotEmpty() && (result.last().isWhitespace() ||
					!result.last().isValidCharacter())
			) {
				result = result.dropLast(1)
			}

			return@withIOContext result
		}
	}

	/**
	 * Determines if a character is considered "valid" for the tail of a truncated string.
	 *
	 * Valid characters include:
	 * * Any letter or digit.
	 * * Special symbols: `_`, `-`, `.`, `@`, ` `, `[`, ]``, `(`, `)`.
	 *
	 * @return `true` if the character is a letter, digit, or in the allowed symbol set.
	 */
	@JvmStatic
	fun Char.isValidCharacter(): Boolean {
		return this.isLetterOrDigit() ||
			this in setOf('_', '-', '.', '@', ' ', '[', ']', '(', ')')
	}

	/**
	 * Concatenates multiple strings into a single string using a specified delimiter.
	 *
	 * This is an asynchronous wrapper around [joinToString]. It is useful for building
	 * CSV lines, path strings, or formatted lists from a variable number of arguments.
	 *
	 * @param delimiter The string to insert between each element.
	 * @param elements A vararg array of strings to be joined.
	 * @return A single concatenated string, or an empty string if no elements were provided.
	 */
	@JvmStatic
	suspend fun join(delimiter: String, vararg elements: String): String {
		return withIOContext {
			if (elements.isEmpty()) return@withIOContext ""
			val result = elements.joinToString(separator = delimiter)
			return@withIOContext result
		}
	}

	/**
	 * Reverses the sequence of characters in a string.
	 *
	 * This operation is performed asynchronously. For very long strings, this prevents
	 * UI freezing while the characters are being reordered in memory.
	 *
	 * @param input The string to be reversed.
	 * @return The reversed string, or null if the input was null.
	 */
	@JvmStatic
	suspend fun reverse(input: String?): String? {
		return withIOContext {
			if (input == null) return@withIOContext null
			// StringBuilder.reverse() is an efficient in-place reversal algorithm
			val result = StringBuilder(input).reverse().toString()
			return@withIOContext result
		}
	}

	/**
	 * Capitalizes only the first character of the provided string.
	 *
	 * If the first character is already uppercase, the original string is returned
	 * to avoid unnecessary object allocation.
	 *
	 * @param string The string to capitalize.
	 * @return The string with the first letter capitalized, or null if input was null/empty.
	 */
	@JvmStatic
	suspend fun capitalizeFirstLetter(string: String?): String? {
		return withIOContext {
			if (string.isNullOrEmpty()) return@withIOContext null
			val first = string[0]
			// Check if transformation is actually needed
			val capitalized = if (Character.isUpperCase(first)) {
				string
			} else {
				first.uppercaseChar().toString() + string.substring(1)
			}
			return@withIOContext capitalized
		}
	}

	/**
	 * Capitalizes the first letter of every word in a string (Title Case).
	 *
	 * This method normalizes the input by trimming leading/trailing whitespace and
	 * collapsing multiple spaces into a single separator. It uses the system's
	 * default locale for title-casing to ensure correct linguistic transformations.
	 *
	 * ### Example:
	 * "hello   world" -> "Hello World"
	 *
	 * @param input The string containing words to capitalize.
	 * @return The transformed string in Title Case, or the original input if null/blank.
	 */
	@JvmStatic
	suspend fun capitalizeWords(input: String?): String? {
		return withIOContext {
			if (input.isNullOrBlank()) return@withIOContext input

			return@withIOContext input
				.trim()
				// Split by one or more whitespace characters
				.split("\\s+".toRegex())
				.joinToString(" ") { word ->
					word.replaceFirstChar {
						if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString()
					}
				}
		}
	}

	/**
	 * Converts a raw HTML string into a [Spanned] object for UI display.
	 * * This method uses the Android [Html.fromHtml] utility with [FROM_HTML_MODE_COMPACT]
	 * to parse HTML tags into displayable spans (like bold, italic, or colored text).
	 * * @param htmlString The source string containing HTML tags.
	 * @return A [Spanned] object ready to be set on a [android.widget.TextView].
	 */
	@JvmStatic
	suspend fun fromHtmlStringToSpanned(htmlString: String): Spanned {
		return withIOContext { fromHtml(htmlString, FROM_HTML_MODE_COMPACT) }
	}

	/**
	 * Retrieves an HTML string from a raw resource file.
	 * * This is a convenience wrapper around [convertRawHtmlFileToString] to maintain
	 * consistent naming conventions for HTML-specific resource fetching.
	 * * @param resId The resource ID of the raw file (e.g., R.raw.terms_of_service).
	 * @return The contents of the file as a single [String].
	 */
	@JvmStatic
	suspend fun getHtmlString(resId: Int): String {
		return withIOContext {
			convertRawHtmlFileToString(resId)
		}
	}

	/**
	 * Reads a raw resource file and converts its entire content into a String.
	 * * This method performs low-level I/O operations by opening an [InputStream],
	 * reading it line-by-line using a [BufferedReader], and aggregating the result
	 * into a [StringBuilder].
	 * * ### Error Handling:
	 * * It catches and logs [Throwable] during the read process to prevent app crashes.
	 * * It ensures that the [InputStream] and [BufferedReader] are closed in the `finally`
	 * block to prevent memory leaks or file descriptor exhaustion.
	 * * @param resourceId The raw resource ID to be processed.
	 * @return The full text content of the file, or an empty string if an error occurs.
	 */
	@JvmStatic
	suspend fun convertRawHtmlFileToString(resourceId: Int): String {
		return withIOContext {
			val inputStream = INSTANCE.resources.openRawResource(resourceId)
			val reader = BufferedReader(InputStreamReader(inputStream))
			val stringBuilder = StringBuilder()
			var line: String?

			try {
				while (reader.readLine().also { line = it } != null) {
					stringBuilder.append(line)
				}
			} catch (error: Throwable) {
				logger.e("Error converting raw html file to string:", error)
			} finally {
				try {
					inputStream.close()
					reader.close()
				} catch (error: Exception) {
					logger.e("Error closing stream or reader:", error)
				}
			}

			stringBuilder.toString()
		}
	}

	/**
	 * Counts the total number of occurrences of a specific character within a string.
	 *
	 * This operation is performed asynchronously to handle potentially massive strings
	 * without blocking the UI thread.
	 *
	 * @param input The source string to search. If null, returns 0.
	 * @param char The character to look for. If null, returns 0.
	 * @return The total count of matching characters found in the input.
	 */
	@JvmStatic
	suspend fun countOccurrences(input: String?, char: Char?): Int {
		return withIOContext {
			// Return 0 early if either parameter is missing to avoid unnecessary processing
			if (input == null || char == null) return@withIOContext 0
			val count = input.count { it == char }
			return@withIOContext count
		}
	}

	/**
	 * Filters out empty or blank lines from a multiline string.
	 *
	 * This method splits the input by the newline character, removes any lines that are
	 * empty or contain only whitespace, and reconstructs the string.
	 *
	 * @param input The multiline string to be cleaned.
	 * @return The filtered string with non-blank lines joined by newlines,
	 * or null if the input was null or empty.
	 */
	@JvmStatic
	suspend fun removeEmptyLines(input: String?): String? {
		return withIOContext {
			if (input.isNullOrEmpty()) return@withIOContext null
			return@withIOContext input.split("\n")
				.filter { it.isNotBlank() }
				.joinToString("\n")
		}
	}

	/**
	 * Formats a raw number into a human-readable "shorthand" string (e.g., 1.5K, 2M).
	 *
	 * This utility scales numbers by powers of 1000 and appends the corresponding
	 * unit (K, M, B, T). It handles integer-like values cleanly (1000 -> 1K) and
	 * provides one decimal place for fractional values (1500 -> 1.5K).
	 *
	 * ### Formatting Logic:
	 * * < 1,000: Returns the raw number as a string.
	 * * 1,000 - 999,999: Appends "K" (Thousands).
	 * * 1,000,000 - 999,999,999: Appends "M" (Millions).
	 * * 1,000,000,000+: Supports Billions (B) and Trillions (T).
	 *
	 * @param count The long value (usually view counts or likes) to format.
	 * @return A localized string representation of the count with a suffix.
	 */
	@JvmStatic
	suspend fun formatViewCounts(count: Long): String {
		return withIOContext {
			if (count < 1000) return@withIOContext count.toString()

			val units = arrayOf("K", "M", "B", "T")
			var value = count.toDouble()
			var unitIndex = -1

			// Incrementally divide by 1000 until the value is below 1000
			while (value >= 1000 && unitIndex < units.lastIndex) {
				value /= 1000
				unitIndex++
			}

			// If the value is a whole number, drop the decimal point
			return@withIOContext if (value % 1.0 == 0.0) {
				"${value.toInt()}${units[unitIndex]}"
			} else {
				// Format to 1 decimal place using the device's default locale
				String.format(getDefault(), "%.1f%s", value, units[unitIndex])
			}
		}
	}

}
