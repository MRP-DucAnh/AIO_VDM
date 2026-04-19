package lib.device

import java.text.*
import java.time.*
import java.time.LocalDateTime.*
import java.time.format.*
import java.util.*
import java.util.Locale.*
import java.util.concurrent.*

object DateTimeUtils {

	@JvmStatic
	val dateFormatter: DateTimeFormatter =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", ENGLISH)

	@JvmStatic
	val timeFormatter12Hour: DateTimeFormatter =
		DateTimeFormatter.ofPattern("hh:mm a", ENGLISH)

	@JvmStatic
	val timeFormatter24Hour: DateTimeFormatter =
		DateTimeFormatter.ofPattern("HH:mm", ENGLISH)

	@JvmStatic
	fun getCurrentDateTime(): String {
		return now().format(dateFormatter)
	}

	@JvmStatic
	fun getCurrentDate(format: String?): String {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		return now().format(formatter)
	}

	@JvmStatic
	fun getCurrentTime(format: String?): String {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		return now().format(formatter)
	}

	@JvmStatic
	fun timestampToDateString(timestamp: Long, format: String?): String {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		val dateTime = ofInstant(Date(timestamp).toInstant(), ZoneId.systemDefault())
		return dateTime.format(formatter)
	}

	@JvmStatic
	fun dateStringToTimestamp(dateString: String?, format: String?): Long {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		val dateTime = parse(dateString, formatter)
		return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()).time
	}

	@JvmStatic
	fun getDaysDifference(startDate: String?, endDate: String?, format: String?): Long {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		val start = parse(startDate, formatter)
		val end = parse(endDate, formatter)
		return Duration.between(start, end).toDays()
	}

	@JvmStatic
	fun formatDateWithSuffix(date: Date): String {
		val dayFormatter = SimpleDateFormat("d", US)
		val monthFormatter = SimpleDateFormat("MMM", US)
		val timeFormatter = SimpleDateFormat("HH:mm", US)

		val day = dayFormatter.format(date).toInt()
		val dayWithSuffix = "$day${getDayOfMonthSuffix(day)}"

		val dateFormat = timeFormatter.format(date)
		val monthFormat = monthFormatter.format(date)
		return "$dayWithSuffix $monthFormat ($dateFormat)"
	}

	@JvmStatic
	fun isCurrentTimeInRange(startTime: String?,
	                         endTime: String?, format: String?): Boolean {
		val formatter = DateTimeFormatter.ofPattern(format, getDefault())
		val start = parse(startTime, formatter)
		val end = parse(endTime, formatter)
		val currentTime = now()
		return currentTime.isAfter(start) && currentTime.isBefore(end)
	}

	@JvmStatic
	fun formatDateString(dateString: String?,
	                     fromFormat: String?, toFormat: String?): String? {
		val fromFormatter = DateTimeFormatter.ofPattern(fromFormat, getDefault())
		val toFormatter = DateTimeFormatter.ofPattern(toFormat, getDefault())
		val dateTime = parse(dateString, fromFormatter)
		return dateTime.format(toFormatter)
	}

	@JvmStatic
	fun getCurrentTimeIn12HourFormat(): String {
		return now().format(timeFormatter12Hour)
	}

	@JvmStatic
	fun getCurrentTimeIn24HourFormat(): String {
		return now().format(timeFormatter24Hour)
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
		return when {
			day in 11..13 -> "th"
			else -> when (day % 10) {
				1 -> "st"
				2 -> "nd"
				3 -> "rd"
				else -> "th"
			}
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
	fun formatTime(milliseconds: Long): String {
		val totalSeconds = milliseconds / 1000
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
	fun getDaysPassedSince(lastModifiedTime: Long): Long {
		val currentTime = System.currentTimeMillis()
		val timeDifference = currentTime - lastModifiedTime
		return TimeUnit.MILLISECONDS.toDays(timeDifference)
	}

	@JvmStatic
	fun millisToDateTimeString(millis: Long): String {
		val dateTime = ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
		val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
		return dateTime.format(formatter)
	}

	@JvmStatic
	fun dateTimeStringToMillis(dateTimeString: String): Long {
		val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
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
			String.format(getDefault(), "%d:%02d:%02d", hour, minute, second)
		} else {
			String.format(getDefault(), "%02d:%02d", minute, second)
		}

		return "$timeString $suffix"
	}

	@JvmStatic
	fun formatTimeDurationToString(durationMillis: Long): String {
		val totalSeconds = durationMillis / 1000
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60
		return String.format(getDefault(),
		                     "%02d:%02d:%02d",
		                     hours, minutes, seconds)
	}
}
