package lib.texts

import android.text.*
import android.text.Html.*
import androidx.annotation.*
import app.core.AIOApp.Companion.INSTANCE
import lib.process.*
import lib.process.LocalizationHelper.getLocalizedString
import java.io.*
import java.util.Locale.*

object CommonTextUtils {

	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	fun removeDuplicateSlashes(input: String?): String? {
		if (input == null) return null
		val result = input.replace("/{2,}".toRegex(), "/")
		return result
	}

	@JvmStatic
	fun getText(@StringRes resID: Int): String {
		return getLocalizedString(INSTANCE, resID)
	}

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

	@JvmStatic
	suspend fun safeCutString(input: String?, maxLength: Int = 60): String? {
		return withIOContext {
			if (input == null) return@withIOContext null
			val codePointCount = input.codePointCount(0, input.length)
			if (codePointCount <= maxLength) return@withIOContext input

			val safeLength = minOf(maxLength, codePointCount)
			val endIndex = input.offsetByCodePoints(0, safeLength)
			var result = input.take(endIndex)

			while (result.isNotEmpty() && (result.last().isWhitespace() ||
					!result.last().isValidCharacter())
			) result = result.dropLast(1)

			return@withIOContext result
		}
	}

	@JvmStatic
	fun Char.isValidCharacter(): Boolean {
		return this.isLetterOrDigit() ||
			this in setOf('_', '-', '.', '@', ' ', '[', ']', '(', ')')
	}

	@JvmStatic
	suspend fun join(delimiter: String, vararg elements: String): String {
		return withIOContext {
			if (elements.isEmpty()) return@withIOContext ""
			val result = elements.joinToString(separator = delimiter)
			return@withIOContext result
		}
	}


	@JvmStatic
	suspend fun reverse(input: String?): String? {
		return withIOContext {
			if (input == null) return@withIOContext null
			val result = StringBuilder(input).reverse().toString()
			return@withIOContext result
		}
	}

	@JvmStatic
	suspend fun capitalizeFirstLetter(string: String?): String? {
		return withIOContext {
			if (string.isNullOrEmpty()) return@withIOContext null
			val first = string[0]
			val capitalized = if (Character.isUpperCase(first)) string
			else first.uppercaseChar().toString() + string.substring(1)
			return@withIOContext capitalized
		}
	}

	@JvmStatic
	suspend fun capitalizeWords(input: String?): String? {
		return withIOContext {
			if (input.isNullOrBlank()) return@withIOContext input
			return@withIOContext input
				.trim()
				.split("\\s+".toRegex())
				.joinToString(" ") { word ->
					word.replaceFirstChar {
						if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString()
					}
				}
		}
	}

	@JvmStatic
	suspend fun fromHtmlStringToSpanned(htmlString: String): Spanned {
		return withIOContext { fromHtml(htmlString, FROM_HTML_MODE_COMPACT) }
	}

	@JvmStatic
	suspend fun getHtmlString(resId: Int): String {
		return withIOContext {
			convertRawHtmlFileToString(resId)
		}
	}

	@JvmStatic
	suspend fun convertRawHtmlFileToString(resourceId: Int): String {
		return withIOContext {
			val inputStream = INSTANCE.resources.openRawResource(resourceId)
			val reader = BufferedReader(InputStreamReader(inputStream))
			val stringBuilder = StringBuilder()
			var line: String?

			try {
				while (reader.readLine()
						.also { line = it } != null
				) stringBuilder.append(line)
			} catch (error: Throwable) {
				logger.e("Error converting raw html file to string:", error)
			} finally {
				try {
					inputStream.close()
					reader.close()
				} catch (error: Exception) {
					logger.e("Error converting raw html file to string:", error)
				}
			}

			stringBuilder.toString()
		}
	}

	@JvmStatic
	suspend fun countOccurrences(input: String?, char: Char?): Int {
		return withIOContext {
			if (input == null || char == null) return@withIOContext 0
			val count = input.count { it == char }
			return@withIOContext count
		}
	}

	@JvmStatic
	suspend fun removeEmptyLines(input: String?): String? {
		return withIOContext {
			if (input.isNullOrEmpty()) return@withIOContext null
			return@withIOContext input.split("\n")
				.filter { it.isNotBlank() }
				.joinToString("\n")
		}
	}

	@JvmStatic
	suspend fun formatViewCounts(count: Long): String {
		return withIOContext {
			if (count < 1000) return@withIOContext count.toString()

			val units = arrayOf("K", "M", "B", "T")
			var value = count.toDouble()
			var unitIndex = -1

			while (value >= 1000 && unitIndex < units.lastIndex) {
				value /= 1000
				unitIndex++
			}

			return@withIOContext if (value % 1.0 == 0.0) {
				"${value.toInt()}${units[unitIndex]}"
			} else {
				String.format(getDefault(), "%.1f%s", value, units[unitIndex])
			}
		}
	}

}
