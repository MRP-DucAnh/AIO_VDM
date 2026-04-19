package lib.files

import com.googlecode.mp4parser.*
import com.googlecode.mp4parser.authoring.*
import com.googlecode.mp4parser.authoring.builder.*
import com.googlecode.mp4parser.authoring.container.mp4.*
import kotlinx.coroutines.*
import lib.process.*
import java.io.*

object MP4FileUtility {
	private val logger = LogHelperUtils.from(javaClass)
	const val TMP_MOOV_OPTIMIZED_PREFIX = "moov_optimize_"
	const val TMP_MOOV_OPTIMIZED_SUFFIX = ".mp4"

	@JvmStatic
	private suspend fun isValidMp4FileAdvanced(file: File): Boolean {
		if (!file.exists() || file.length() < 100) return false

		return try {
			val dataSource = FileDataSourceImpl(file.absolutePath)
			try {
				val movie = MovieCreator.build(dataSource)
				movie.tracks.isNotEmpty() &&
					movie.tracks.all { it.samples.isNotEmpty() }
			} catch (error: Exception) {
				logger.e("Advanced MP4 validation failed:", error)
				false

			} finally {
				dataSource.close()
			}
		} catch (error: Exception) {
			logger.e("Error during advanced validation:", error)
			false
		}
	}

	@JvmStatic
	private suspend fun isValidMp4File(file: File): Boolean {
		if (!file.exists() || file.length() < 12) return false

		return try {
			withContext(Dispatchers.IO) {
				FileInputStream(file).use { fis ->
					val buffer = ByteArray(12)
					val bytesRead = fis.read(buffer)
					if (bytesRead < 12) {
						return@withContext false
					}

					val signature = String(buffer, 4, 4, Charsets.US_ASCII)
					(signature == "ftyp")
				}
			}
		} catch (error: Exception) {
			logger.e("Error validating MP4 file:", error)
			false
		}
	}

	@JvmStatic
	suspend fun moveMoovAtomToStart(inputFile: File, outputFile: File): Boolean {
		if (!inputFile.exists()) return false
		if (inputFile.length() == 0L) return false
		if (!inputFile.canRead()) return false

		val outputDir = outputFile.parentFile
		if (outputDir != null && !outputDir.canWrite()) return false

		val requiredSpace = inputFile.length() * 3
		val availableSpace = outputDir?.freeSpace ?: 0L
		if (availableSpace < requiredSpace) return false

		if (!isValidMp4File(inputFile)) return false

		val tempFile = withContext(Dispatchers.IO) {
			File.createTempFile(
				"$TMP_MOOV_OPTIMIZED_PREFIX${inputFile.name}",
				TMP_MOOV_OPTIMIZED_SUFFIX, outputDir
			)
		}

		return try {
			val dataSource = FileDataSourceImpl(inputFile.absolutePath)
			val movie: Movie = try {
				MovieCreator.build(dataSource)
			} catch (error: Exception) {
				dataSource.close()
				return false
			}

			if (movie.tracks.isEmpty()) return false
			val mp4Builder = DefaultMp4Builder()
			val container = mp4Builder.build(movie)

			withContext(Dispatchers.IO) {
				FileOutputStream(tempFile).use { fos ->
					val channel = fos.channel
					container.writeContainer(channel)
					channel.force(true)
				}
			}

			dataSource.close()
			if (!isValidMp4File(tempFile)) {
				tempFile.delete()
				return false
			}

			val inputSize = inputFile.length()
			val tempSize = tempFile.length()
			val sizeRatio = tempSize.toDouble() / inputSize.toDouble()

			if (sizeRatio !in 0.5..1.5) {
				tempFile.delete()
				return false
			}

			if (tempFile.renameTo(outputFile)) {
				if (!isValidMp4FileAdvanced(outputFile)) {
					outputFile.delete()
					return false
				}
				true
			} else false

		} catch (error: Exception) {
			logger.e("Error moving moov atom to start:", error)
			if (tempFile.exists()) runCatching { tempFile.delete() }
			if (outputFile.exists()) runCatching { outputFile.delete() }
			false
		} finally {
			if (tempFile.exists()) tempFile.delete()
		}
	}

	@JvmStatic
	suspend fun isMp4Seekable(file: File): Boolean {
		if (!file.exists() || file.length() < 100) return false

		try {
			withContext(Dispatchers.IO) {
				FileInputStream(file).use { fis ->
					val buffer = ByteArray(1024 * 1024)
					val bytesRead = fis.read(buffer)
					if (bytesRead <= 0) {
						logger.d("Failed to read file: ${file.absolutePath}")
						return@withContext false
					}
					val content = buffer.copyOf(bytesRead)
					if (!content.containsMoovAtomAtStart()) {
						logger.d("'moov' atom not found near start: ${file.name}")
						return@withContext false
					}
				}
			}
		} catch (error: Exception) {
			logger.e("Error validating MP4 file", error)
			return false
		}

		try {
			val dataSource = FileDataSourceImpl(file.absolutePath)
			val movie = MovieCreator.build(dataSource)
			val isValid = movie.tracks.isNotEmpty() &&
				movie.tracks.all { it.samples.isNotEmpty() }
			dataSource.close()
			return isValid
		} catch (error: Exception) {
			logger.e("MP4Parser validation failed: ${error.message}", error)
			return false
		}
	}

	suspend fun ByteArray.containsMoovAtomAtStart(): Boolean {
		var i = 0
		while (i + 8 <= this.size) {
			val size = ((this[i].toInt() and 0xFF) shl 24) or
				((this[i + 1].toInt() and 0xFF) shl 16) or
				((this[i + 2].toInt() and 0xFF) shl 8) or
				(this[i + 3].toInt() and 0xFF)
			if (i + size > this.size || size < 8) break
			val type = String(this, i + 4, 4, Charsets.US_ASCII)
			if (type == "moov") return i <= 1024
			i += size
		}
		return false
	}
}