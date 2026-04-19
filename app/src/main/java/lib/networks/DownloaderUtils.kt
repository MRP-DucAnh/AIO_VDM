package lib.networks

import android.media.*
import android.media.MediaMetadataRetriever.*
import androidx.documentfile.provider.*
import androidx.documentfile.provider.DocumentFile.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.IS_PREMIUM_USER
import app.core.engines.downloader.*
import lib.device.DateTimeUtils.formatVideoDuration
import lib.files.FileSystemUtility.isAudio
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isWritableFile
import lib.process.*
import lib.texts.CommonTextUtils.removeDuplicateSlashes
import java.io.*
import java.text.*
import java.util.*
import kotlin.math.*

object DownloaderUtils {

	private val logger = LogHelperUtils.from(javaClass)
	private val decimalFormat by lazy { DecimalFormat("##.##") }

	@JvmStatic
	fun getFormattedPercentage(download: AIODownload): String {
		return decimalFormat.format(download.progressPercentage.toDouble())
	}

	@JvmStatic fun getFormatted(input: Double): String {
		val format = decimalFormat.format(input)
		return format
	}

	@JvmStatic fun getFormatted(input: Float): String {
		return decimalFormat.format(input.toDouble())
	}

	@JvmStatic
	suspend fun getOptimalNumberOfDownloadParts(totalFileLength: Long): Int {
		val mb1 = 1000000
		val mb5 = (1000000 * 5)
		val mb10 = (1000000 * 10)
		val mb50 = (1000000 * 50)
		val mb100 = (1000000 * 100)
		val mb200 = (1000000 * 200)
		val mb400 = (1000000 * 400)

		return if (IS_PREMIUM_USER) {
			if (totalFileLength < mb1) 1
			else if (totalFileLength < mb5) 1
			else if (totalFileLength < mb10) 2
			else if (totalFileLength < mb50) 3
			else if (totalFileLength < mb100) 5
			else if (totalFileLength < mb200) 10
			else if (totalFileLength < mb400) 12
			else 18
		} else {
			if (totalFileLength < mb1) 1
			else if (totalFileLength < mb5) 2
			else if (totalFileLength < mb10) 2
			else if (totalFileLength < mb50) 3
			else if (totalFileLength < mb100) 3
			else if (totalFileLength < mb200) 4
			else if (totalFileLength < mb400) 5
			else 5
		}
	}

	@JvmStatic
	suspend fun fetchMediaDuration(download: AIODownload): String {
		return withIOContext {
			val downloadedFile: DocumentFile = download.getDestinationDocumentFile()
			if (isAudio(downloadedFile) || isVideo(downloadedFile)) {
				try {
					if (!isWritableFile(downloadedFile)) return@withIOContext ""
					val mediaFileUri = downloadedFile.uri
					val retriever = MediaMetadataRetriever()
					retriever.setDataSource(INSTANCE, mediaFileUri)

					val extractCode = METADATA_KEY_DURATION
					val extractMetadata = retriever.extractMetadata(extractCode)
					val durationMs = extractMetadata?.toLongOrNull()
					retriever.release()

					val formattedDuration = formatVideoDuration(durationMs)
					return@withIOContext "($formattedDuration)"
				} catch (error: Exception) {
					error.printStackTrace()
					return@withIOContext ""
				}
			} else return@withIOContext ""
		}
	}

	@JvmStatic
	suspend fun getHumanReadableFormat(fileSizeInByte: Long): String {
		return withIOContext {
			if (fileSizeInByte < 1024) return@withIOContext "$fileSizeInByte B"
			val exp = (ln(fileSizeInByte.toDouble()) / ln(1024.0)).toInt()
			val pre = "KMGTPE"[exp - 1] + "B"
			return@withIOContext String.format(
				Locale.US, "%.1f %s",
				fileSizeInByte / 1024.0.pow(exp.toDouble()), pre
			)
		}
	}

	@JvmStatic
	suspend fun getHumanReadableSpeed(speedBytesPerSecond: Double): String {
		val oneKB: Long = 1024
		val oneMB = oneKB * 1024
		val oneGB = oneMB * 1024
		val oneTB = oneGB * 1024
		val df = decimalFormat
		return withIOContext {
			when {
				speedBytesPerSecond >= oneTB ->
					df.format(speedBytesPerSecond / oneTB) + "TB/s"

				speedBytesPerSecond >= oneGB ->
					df.format(speedBytesPerSecond / oneGB) + "GB/s"

				speedBytesPerSecond >= oneMB ->
					df.format(speedBytesPerSecond / oneMB) + "MB/s"

				speedBytesPerSecond >= oneKB ->
					df.format(speedBytesPerSecond / oneKB) + "KB/s"
				else -> df.format(speedBytesPerSecond) + "B/s"
			}
		}
	}

	@JvmStatic
	suspend fun generateUniqueDownloadFileName(baseFileName: String): String {
		val timestamp = System.currentTimeMillis()
		val extensionIndex = baseFileName.lastIndexOf('.')
		return if (extensionIndex != -1) {
			"${baseFileName.substring(0, extensionIndex)}_$timestamp${
				baseFileName.substring(extensionIndex)
			}"
		} else {
			"${baseFileName}_$timestamp"
		}
	}

	@JvmStatic
	suspend fun updateSmartCatalogDownloadDir(download: AIODownload) {
		withIOContext {
			if (isSmartDownloadCatalogEnabled(download)) {
				val fileCategoryName = download.getUpdatedCategoryName()
				val aioRootDir = File(download.fileDirectory)
				val aioSubDir = fromFile(aioRootDir).createDirectory(fileCategoryName)
				aioSubDir?.canWrite().let { canWrite ->
					if (canWrite == true) {
						download.fileCategoryName = fileCategoryName
					}
				}
			} else download.fileCategoryName = ""

			val finalDirWithCategory = removeDuplicateSlashes(
				"${download.fileDirectory}/${
					if (download.fileCategoryName.isNotEmpty())
						download.fileCategoryName + "/" else ""
				}"
			)

			finalDirWithCategory?.let { download.fileDirectory = it }
		}
	}

	@JvmStatic
	suspend fun renameIfDownloadFileExistsWithSameName(download: AIODownload) {
		withIOContext {
			var index: Int
			val regex = Regex("^(\\d+)_")

			while (download.getDestinationDocumentFile().exists()) {
				val matchResult = regex.find(download.fileName)
				if (matchResult != null) {
					val currentIndex = matchResult.groupValues[1].toInt()
					download.fileName = download.fileName.replaceFirst(regex, "")
					index = currentIndex + 1
				} else index = 1
				download.fileName = "${index}_${download.fileName}"
			}
		}
	}

	@JvmStatic
	suspend fun validateExistedDownloadedFileName(
		directory: String, fileName: String): String {
		return withIOContext {
			var index: Int
			val regex = Regex("^(\\d+)_")
			var modifiedName = fileName

			while (File(directory, modifiedName).exists()) {
				val matchResult = regex.find(modifiedName)
				if (matchResult != null) {
					val currentIndex = matchResult.groupValues[1].toInt()
					modifiedName = modifiedName.replaceFirst(regex, "")
					index = currentIndex + 1
				} else index = 1
				modifiedName = "${index}_${modifiedName}"
			}
			return@withIOContext modifiedName
		}
	}

	@JvmStatic
	fun isSmartDownloadCatalogEnabled(
		downloadModel: AIODownload): Boolean {
		return downloadModel.config.downloadAutoFolderCatalog
	}

	@JvmStatic
	suspend fun convertToNetscapeCookies(cookieString: String): String {
		return withIOContext {
			val cookies = cookieString.split(";").map { it.trim() }
			val domain = ""
			val path = "/"
			val secure = "FALSE"
			val expiry = "2147483647"

			val stringBuilder = StringBuilder()
			stringBuilder.append("# Netscape HTTP Cookie File\n")
			stringBuilder.append("# This file was generated by the app.\n\n")

			for (cookie in cookies) {
				val parts = cookie.split("=", limit = 2)
				if (parts.size == 2) {
					val name = parts[0].trim()
					val value = parts[1].trim()
					val pattern = "$domain\tFALSE\t$path\t$secure\t$expiry\t$name\t$value\n"
					stringBuilder.append(pattern)
				}
			}
			return@withIOContext stringBuilder.toString()
		}
	}

	@JvmStatic
	suspend fun getVideoResolutionFromUrl(videoUrl: String): Pair<Int, Int>? {
		return withIOContext {
			val retriever = MediaMetadataRetriever()
			return@withIOContext try {
				retriever.setDataSource(videoUrl, HashMap())
				val width = retriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toInt()
				val height = retriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toInt()
				if (width != null && height != null) {
					logger.d("Video resolution for $videoUrl: ${width}x$height")
					Pair(width, height)
				} else {
					logger.e("Video resolution not found for $videoUrl")
					null
				}
			} catch (error: Exception) {
				logger.e("Error extracting video resolution: ${error.message}", error)
				logger.d("Video resolution for $videoUrl: null")
				null
			} finally {
				retriever.release()
			}
		}
	}

	@JvmStatic
	suspend fun getVideoDurationFromUrl(videoUrl: String): Long {
		return withIOContext {
			try {
				val retriever = MediaMetadataRetriever()
				retriever.setDataSource(videoUrl, HashMap())

				val durationStr = retriever.extractMetadata(METADATA_KEY_DURATION)
				val durationMs = durationStr?.toLongOrNull() ?: 0L
				logger.d("Video duration extracted: ${durationMs}ms")

				retriever.release()
				durationMs
			} catch (error: Exception) {
				logger.e("Error extracting video duration: ${error.message}", error)
				logger.d("Video duration extracted: 0ms")
				0L
			}
		}
	}
}