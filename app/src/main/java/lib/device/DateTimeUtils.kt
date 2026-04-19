package lib.device

import app.core.*
import java.text.*
import java.time.*
import java.time.LocalDateTime.*
import java.time.format.*
import java.time.format.DateTimeFormatter.*
import java.util.*
import java.util.Locale.*
import java.util.concurrent.*

object DateTimeUtils {

	private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()
	private val defaultDateFormatter = ofPattern(APP_DEFAULT_DATE_TIME_FORMAT, ENGLISH)
	private val time12HFormatter = ofPattern(APP_TIME_FORMAT_12_HOUR, ENGLISH)
	private val time24HFormatter = ofPattern(APP_TIME_FORMAT_24_HOUR, ENGLISH)

	private fun getOrCreateFormatter(
		pattern: String, locale: Locale = getDefault()
	): DateTimeFormatter {
		val key = "$pattern-${locale.language}"
		return formatterCache.getOrPut(key) {
			ofPattern(pattern, locale)
		}
	}

	@JvmStatic
	fun getCurrentDateTime(): String {
		return now().format(defaultDateFormatter)
	}

	@JvmStatic
	fun getCurrentDate(format: String?): String {
		val formatter = ofPattern(format, getDefault())
		return now().format(formatter)
	}

	@JvmStatic
	fun getCurrentTime(format: String?): String {
		val formatter = ofPattern(format, getDefault())
		return now().format(formatter)
	}

	@JvmStatic
	fun timestampToDateString(timestamp: Long, format: String): String {
		val instant = Instant.ofEpochMilli(timestamp)
		val dateTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
		return dateTime.format(getOrCreateFormatter(format))
	}

	@JvmStatic
	fun dateStringToTimestamp(dateString: String?, format: String?): Long {
		val formatter = ofPattern(format, getDefault())
		val dateTime = parse(dateString, formatter)
		return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()).time
	}

	@JvmStatic
	fun getDaysDifference(startDate: String?, endDate: String?, format: String?): Long {
		val formatter = ofPattern(format, getDefault())
		val start = parse(startDate, formatter)
		val end = parse(endDate, formatter)
		return Duration.between(start, end).toDays()
	}

	@JvmStatic
	fun formatDateWithSuffix(timestamp: Long): String {
		val ofEpochMilli = Instant.ofEpochMilli(timestamp)
		val systemDefault = ZoneId.systemDefault()
		val dt = ZonedDateTime.ofInstant(ofEpochMilli, systemDefault)
		val day = dt.dayOfMonth
		val month = dt.format(getOrCreateFormatter("MMM", US))
		val time = dt.format(getOrCreateFormatter("HH:mm", US))

		return "${day}${getDayOfMonthSuffix(day)} $month ($time)"
	}

	@JvmStatic
	fun isCurrentTimeInRange(startTime: String?,
	                         endTime: String?, format: String?): Boolean {
		val formatter = ofPattern(format, getDefault())
		val start = parse(startTime, formatter)
		val end = parse(endTime, formatter)
		val currentTime = now()
		return currentTime.isAfter(start) && currentTime.isBefore(end)
	}

	@JvmStatic
	fun formatDateString(dateString: String?,
	                     fromFormat: String?, toFormat: String?): String? {
		val fromFormatter = ofPattern(fromFormat, getDefault())
		val toFormatter = ofPattern(toFormat, getDefault())
		val dateTime = parse(dateString, fromFormatter)
		return dateTime.format(toFormatter)
	}

	@JvmStatic
	fun getCurrentTimeIn12HourFormat(): String {
		return now().format(time12HFormatter)
	}

	@JvmStatic
	fun getCurrentTimeIn24HourFormat(): String {
		return now().format(time24HFormatter)
	}

	@JvmStatic
	fun formatLastModifiedDate(lastModifiedTimeDate: Long): String {
		val calendar = Calendar.getInstance().apply {
			timeInMillis = lastModifiedTimeDate
		}
		val day = calendar.get(Calendar.DAY_OF_MONTH)
		val daySuffix = getDayOfMonthSuffix(day)
		val dateFormat = SimpleDateFormat("MMM", getDefault())
		val month = dateFormat.format(calendar.time)

		return "$day$daySuffix $month"
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

	@JvmStatic
	fun formatVideoDuration(durationMs: Long?): String {
		if (durationMs == null) return "00:00"

		val totalSeconds = durationMs / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		return if (hours > 0) {
			String.format(getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
		} else {
			String.format(getDefault(), "%02d:%02d", minutes, seconds)
		}
	}

	@JvmStatic
	fun formatTime(milliseconds: Long, includeSuffix: String = ""): String {
		val totalSeconds = milliseconds / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		val time = if (hours > 0)
			String.format(getDefault(),
			              APP_DEFAULT_TIMESTAMP_PATTERN,
			              hours, minutes, seconds)
		else String.format(getDefault(), "%02d:%02d", minutes, seconds)
		return if (includeSuffix.isEmpty()) time else "$time $includeSuffix"
	}

	@JvmStatic
	fun getDaysPassedSince(lastModifiedTime: Long): Long {
		val currentTime = System.currentTimeMillis()
		val timeDifference = currentTime - lastModifiedTime
		return TimeUnit.MILLISECONDS.toDays(timeDifference)
	}

	@JvmStatic
	fun millisToDateTimeString(millis: Long): String {
		val dateTime = ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
		val formatter = ofPattern("dd-MM-yyyy HH:mm:ss")
		return dateTime.format(formatter)
	}

	@JvmStatic
	fun dateTimeStringToMillis(dateTimeString: String): Long {
		val formatter = ofPattern("dd-MM-yyyy HH:mm:ss")
		val dateTime = parse(dateTimeString, formatter)
		return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
	}

	@JvmStatic
	fun calculateTime(millis: Float, suffix: String = ""): String {
		val totalSeconds = (millis / 1000).toInt()
		val second = totalSeconds % 60
		val minute = (totalSeconds / 60) % 60
		val hour = totalSeconds / 3600

		val timeString = if (hour > 0) {
			String.format(getDefault(),
			              APP_DEFAULT_DURATION_PATTERN,
			              hour, minute, second)
		} else String.format(getDefault(),
		                     APP_DEFAULT_TIME_PATTERN, minute, second)
		return "$timeString $suffix"
	}

	@JvmStatic
	fun formatTimeDurationToString(durationMillis: Long): String {
		val totalSeconds = durationMillis / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60
		return String.format(getDefault(),
		                     APP_DEFAULT_TIMESTAMP_PATTERN,
		                     hours, minutes, seconds)
	}
}
