package lib.device

import app.core.*
import java.time.*
import java.time.format.*
import java.time.format.DateTimeFormatter.*
import java.util.*
import java.util.concurrent.*

object DateTimeUtils {

	private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()

	private val defaultDateFormatter by lazy {
		getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT, Locale.ENGLISH)
	}
	private val time12HFormatter by lazy {
		getOrCreateFormatter(APP_TIME_FORMAT_12_HOUR, Locale.ENGLISH)
	}

	private val time24HFormatter by lazy {
		getOrCreateFormatter(APP_TIME_FORMAT_24_HOUR, Locale.ENGLISH)
	}

	private fun getOrCreateFormatter(pattern: String?,
	                                 locale: Locale = Locale.getDefault())
		: DateTimeFormatter {
		val safePattern = pattern ?: APP_DEFAULT_DATE_TIME_FORMAT
		val key = "$safePattern-${locale.language}-${locale.country}"
		return formatterCache.getOrPut(key) {
			ofPattern(safePattern, locale)
		}
	}

	@JvmStatic
	fun getCurrentDateTime(): String {
		return LocalDateTime.now().format(defaultDateFormatter)
	}

	@JvmStatic
	fun getCurrentDate(format: String?): String {
		return LocalDateTime.now().format(getOrCreateFormatter(format))
	}

	@JvmStatic
	fun getCurrentTime(format: String?): String {
		return LocalDateTime.now().format(getOrCreateFormatter(format))
	}

	@JvmStatic
	fun timestampToDateString(timestamp: Long, format: String): String {
		val instant = Instant.ofEpochMilli(timestamp)
		val dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
		return dt.format(getOrCreateFormatter(format))
	}

	@JvmStatic
	fun dateStringToTimestamp(dateString: String?, format: String?): Long {
		if (dateString.isNullOrEmpty()) return 0L
		return try {
			val createFormatter = getOrCreateFormatter(format)
			val dateTime = LocalDateTime.parse(dateString, createFormatter)
			dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
		} catch (error: Exception) {
			error.printStackTrace()
			0L
		}
	}

	@JvmStatic
	fun getDaysDifference(startDate: String?, endDate: String?, format: String?): Long {
		if (startDate.isNullOrEmpty() || endDate.isNullOrEmpty()) return 0L
		return try {
			val formatter = getOrCreateFormatter(format)
			val start = LocalDateTime.parse(startDate, formatter)
			val end = LocalDateTime.parse(endDate, formatter)
			Duration.between(start, end).toDays()
		} catch (e: Exception) {
			0L
		}
	}

	@JvmStatic
	fun formatDateWithSuffix(timestamp: Long): String {
		val instant = Instant.ofEpochMilli(timestamp)
		val dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
		val day = dt.dayOfMonth
		val month = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_MONTH, Locale.US))
		val time = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_24_HOUR, Locale.US))
		return "${day}${getDayOfMonthSuffix(day)} $month ($time)"
	}

	@JvmStatic
	fun isCurrentTimeInRange(startTime: String?,
	                         endTime: String?, format: String?): Boolean {
		if (startTime.isNullOrEmpty() || endTime.isNullOrEmpty()) return false
		return try {
			val formatter = getOrCreateFormatter(format)
			val start = LocalDateTime.parse(startTime, formatter)
			val end = LocalDateTime.parse(endTime, formatter)
			val now = LocalDateTime.now()
			now.isAfter(start) && now.isBefore(end)
		} catch (e: Exception) {
			false
		}
	}

	@JvmStatic
	fun formatDateString(dateString: String?,
	                     fromFormat: String?, toFormat: String?): String? {
		if (dateString.isNullOrEmpty()) return null
		return try {
			val formatter = getOrCreateFormatter(fromFormat)
			val dateTime = LocalDateTime.parse(dateString, formatter)
			dateTime.format(getOrCreateFormatter(toFormat))
		} catch (error: Exception) {
			error.printStackTrace()
			null
		}
	}

	@JvmStatic
	fun getCurrentTimeIn12HourFormat(): String {
		return LocalDateTime.now().format(time12HFormatter)
	}

	@JvmStatic
	fun getCurrentTimeIn24HourFormat(): String {
		return LocalDateTime.now().format(time24HFormatter)
	}

	@JvmStatic
	fun formatLastModifiedDate(lastModifiedTimeDate: Long): String {
		val instant = Instant.ofEpochMilli(lastModifiedTimeDate)
		val dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
		val day = dt.dayOfMonth
		val month = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_MONTH))
		return "$day${getDayOfMonthSuffix(day)} $month"
	}

	@JvmStatic
	fun getDayOfMonthSuffix(day: Int): String {
		return if (day in 11..13) "th"
		else when (day % 10) {
			1 -> "st"
			2 -> "nd"
			3 -> "rd"
			else -> "th"
		}
	}

	/**
	 * Shared duration formatting logic to keep code DRY.
	 */
	private fun composeDuration(totalSeconds: Long): String {
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		return if (hours > 0) {
			String.format(Locale.getDefault(),
			              APP_DEFAULT_TIMESTAMP_PATTERN,
			              hours, minutes, seconds)
		} else {
			String.format(Locale.getDefault(),
			              APP_DEFAULT_TIME_PATTERN,
			              minutes, seconds)
		}
	}

	@JvmStatic
	fun formatVideoDuration(durationMs: Long?): String {
		if (durationMs == null || durationMs <= 0) return APP_TIME_EMPTY
		return composeDuration(durationMs / 1000)
	}

	@JvmStatic
	fun formatTime(milliseconds: Long, includeSuffix: String = ""): String {
		val time = composeDuration(milliseconds / 1000)
		return if (includeSuffix.isEmpty()) time else "$time $includeSuffix"
	}

	@JvmStatic
	fun getDaysPassedSince(lastModifiedTime: Long): Long {
		val timeDifference = System.currentTimeMillis() - lastModifiedTime
		return TimeUnit.MILLISECONDS.toDays(timeDifference)
	}

	@JvmStatic
	fun millisToDateTimeString(millis: Long): String {
		val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
		return dt.format(getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT))
	}

	@JvmStatic
	fun dateTimeStringToMillis(dateTimeString: String): Long {
		return try {
			val formatter = getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT)
			val dt = LocalDateTime.parse(dateTimeString, formatter)
			dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
		} catch (e: Exception) {
			0L
		}
	}

	@JvmStatic
	fun calculateTime(millis: Float, suffix: String = ""): String {
		val time = composeDuration((millis / 1000).toLong())
		return if (suffix.isEmpty()) time else "$time $suffix"
	}

	@JvmStatic
	fun formatTimeDurationToString(durationMillis: Long): String {
		return composeDuration(durationMillis / 1000)
	}
}