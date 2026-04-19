package lib.files

import java.text.*

object FileSizeFormatter {

	private const val ONE_KB: Long = 1024
	private const val ONE_MB = ONE_KB * 1024
	private const val ONE_GB = ONE_MB * 1024

	@JvmStatic
	suspend fun humanReadableSizeOf(size: Long): String {
		require(size >= 0) { "File size cannot be negative: $size" }
		return humanReadableSizeOf(size.toDouble())
	}

	@JvmStatic
	suspend fun humanReadableSizeOf(size: Double): String {
		require(size >= 0) { "File size cannot be negative: $size" }

		val df = DecimalFormat("##.##")
		return when {
			size >= ONE_GB -> formatUnit(size / ONE_GB, "GB", df)
			size >= ONE_MB -> formatUnit(size / ONE_MB, "MB", df)
			size >= ONE_KB -> formatUnit(size / ONE_KB, "KB", df)
			else -> formatUnit(size, "B", df)
		}
	}

	private fun formatUnit(
		value: Double, unit: String, df: DecimalFormat): String {
		val formatted = "${df.format(value)} $unit"
		return formatted
	}
}
