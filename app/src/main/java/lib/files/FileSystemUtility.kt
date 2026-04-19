@file:Suppress("DEPRECATION")

package lib.files

import android.content.*
import android.content.Context.*
import android.content.Intent.*
import android.net.*
import android.os.*
import android.provider.OpenableColumns.*
import android.provider.Settings.*
import android.webkit.*
import androidx.core.net.*
import androidx.documentfile.provider.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOApp.Companion.internalDataFolder
import com.aio.*
import com.anggrayudi.storage.file.*
import lib.files.FileExtensions.ARCHIVE_EXTENSIONS
import lib.files.FileExtensions.DOCUMENT_EXTENSIONS
import lib.files.FileExtensions.IMAGE_EXTENSIONS
import lib.files.FileExtensions.MUSIC_EXTENSIONS
import lib.files.FileExtensions.PROGRAM_EXTENSIONS
import lib.files.FileExtensions.VIDEO_EXTENSIONS
import lib.process.*
import lib.texts.CommonTextUtils.getText
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets.*
import java.security.*
import java.util.*

object FileSystemUtility {

	@JvmStatic
	fun getPrimaryStoragePathFallback(): String {
		return Environment.getExternalStorageDirectory().absolutePath
	}

	@JvmStatic
	suspend fun openAllFilesAccessSettings(context: Context) {
		withMainContext {
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					val action = ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
					val intent = Intent(action).apply {
						data = "package:${context.packageName}".toUri()
					}
					context.startActivity(intent)
				} else {
					val action = ACTION_APPLICATION_DETAILS_SETTINGS
					val intent = Intent(action).apply {
						data = "package:${context.packageName}".toUri()
					}
					context.startActivity(intent)
				}
			} catch (error: Exception) {
				error.printStackTrace()
				val action = ACTION_APPLICATION_DETAILS_SETTINGS
				val intent = Intent(action).apply {
					data = "package:${context.packageName}".toUri()
				}
				context.startActivity(intent)
			}
		}
	}

	@JvmStatic
	suspend fun hasFullFileSystemAccess(context: Context): Boolean {
		return withMainContext {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				Environment.isExternalStorageManager()
			} else {
				val readPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
				val writePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
				val readGranted = context.checkSelfPermission(readPermission) ==
					android.content.pm.PackageManager.PERMISSION_GRANTED
				val writeGranted = context.checkSelfPermission(writePermission) ==
					android.content.pm.PackageManager.PERMISSION_GRANTED
				readGranted && writeGranted
			}
		}
	}

	@JvmStatic
	suspend fun updateMediaStore() {
		withIOContext {
			try {
				if (downloadSystem.isInitializing) return@withIOContext
				var index = 0
				while (index < downloadSystem.finishedDownloadDataModels.size) {
					val model = downloadSystem.finishedDownloadDataModels[index]
					val downloadedFile = model.getDestinationFile()
					addToMediaStore(downloadedFile)
					index++
				}
			} catch (error: Exception) {
				error.printStackTrace()
			}
		}
	}

	@JvmStatic
	suspend fun extractFileNameFromContentDisposition(
		contentDisposition: String?): String? {
		return withIOContext {
			if (!contentDisposition.isNullOrEmpty()) {
				val filenameRegex = """(?i)filename=["']?([^";]+)""".toRegex()
				val filenameMatch = filenameRegex.find(contentDisposition)
				if (filenameMatch != null) {
					val filename = filenameMatch.groupValues[1]
					return@withIOContext filename
				}
			}
			return@withIOContext null
		}
	}

	@JvmStatic
	suspend fun decodeURLFileName(encodedString: String): String {
		return withIOContext {
			try {
				val decodedFileName = URLDecoder.decode(encodedString, UTF_8.name())
				decodedFileName
			} catch (error: Exception) {
				error.printStackTrace()
				encodedString
			}
		}
	}

	@JvmStatic
	suspend fun getFileNameFromUri(context: Context, uri: Uri): String? {
		return withIOContext {
			try {
				var fileName: String? = null
				if ("content" == uri.scheme) {
					val cursor = context.contentResolver.query(uri, null, null, null, null)
					if (cursor != null) {
						if (cursor.moveToFirst()) {
							val nameIndex = cursor.getColumnIndex(DISPLAY_NAME)
							if (nameIndex != -1) {
								fileName = cursor.getString(nameIndex)
							}
						}
						cursor.close()
					}
				} else if ("file" == uri.scheme) {
					fileName = File(uri.path!!).name
				}
				return@withIOContext fileName
			} catch (error: Exception) {
				error.printStackTrace()
				return@withIOContext null
			}
		}
	}

	@JvmStatic
	suspend fun getFileFromUri(uri: Uri): File? {
		return withIOContext {
			try {
				val filePath = uri.path
				val file = if (filePath != null) File(filePath) else null
				file
			} catch (error: Exception) {
				error.printStackTrace()
				null
			}
		}
	}

	@JvmStatic
	suspend fun saveStringToInternalStorage(fileName: String, data: String): Boolean {
		return withIOContext {
			try {
				val context = INSTANCE
				val fileOutputStream = context.openFileOutput(fileName, MODE_PRIVATE)
				fileOutputStream.write(data.toByteArray())
				fileOutputStream.close()
				true
			} catch (error: Exception) {
				error.printStackTrace()
				false
			}
		}
	}

	@JvmStatic
	suspend fun readStringFromInternalStorage(fileName: String): String {
		val context = INSTANCE
		return withIOContext {
			try {
				val fileInputStream: FileInputStream = context.openFileInput(fileName)
				val content = fileInputStream.readBytes().toString(Charsets.UTF_8)
				fileInputStream.close()
				return@withIOContext content
			} catch (error: Exception) {
				error.printStackTrace()
				return@withIOContext ""
			}
		}
	}

	@JvmStatic
	fun sanitizeFileNameExtreme(fileName: String): String {
		val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9()@\\[\\]_.-]"), "_")
			.replace(" ", "_")
			.replace("___", "_")
			.replace("__", "_")
		return sanitizedFileName
	}

	@JvmStatic
	fun sanitizeFileNameNormal(fileName: String): String {
		val sanitizedFileName = fileName
			.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}\u0000-\u001F\u007F]"), "_")
			.trimEnd('.')
			.trim()
			.replace(Regex("_+"), "_")
			.replace(" ", "_")
			.replace("___", "_")
			.replace("__", "_")
		return sanitizedFileName
	}

	@JvmStatic
	suspend fun isFileNameValid(fileName: String): Boolean {
		return withIOContext {
			try {
				val internalPath = internalDataFolder.getAbsolutePath(INSTANCE)
				val directory = File(internalPath)
				val tempFile = File(directory, fileName)
				tempFile.createNewFile()
				tempFile.delete()
				return@withIOContext true
			} catch (error: IOException) {
				error.printStackTrace()
				return@withIOContext false
			}
		}
	}

	@JvmStatic
	suspend fun isWritableFile(file: DocumentFile?): Boolean {
		val isWritable = file?.canWrite() ?: false
		return isWritable
	}

	@JvmStatic
	suspend fun hasWriteAccess(folder: DocumentFile?): Boolean {
		return withIOContext {
			if (folder == null) return@withIOContext false
			return@withIOContext try {
				val tempFile = folder.createFile("text/plain", "temp_check_file.txt")
				if (tempFile != null) {
					INSTANCE.contentResolver.openOutputStream(tempFile.uri)?.use { stream ->
						stream.write("test".toByteArray())
						stream.flush()
					}
					tempFile.delete()
					true
				} else false
			} catch (error: IOException) {
				error.printStackTrace()
				false
			}
		}
	}

	@JvmStatic
	suspend fun writeEmptyFile(context: Context,
	                           file: DocumentFile,
	                           fileSize: Long): Boolean {
		return withIOContext {
			try {
				val contentResolver = context.contentResolver
				val outputStream = contentResolver.openOutputStream(file.uri)

				outputStream?.use { stream ->
					val placeholder = ByteArray(fileSize.toInt())
					stream.write(placeholder)
					stream.flush()
				}
				true
			} catch (error: IOException) {
				error.printStackTrace()
				false
			}
		}
	}

	@JvmStatic
	suspend fun generateUniqueFileName(fileDirectory: DocumentFile,
	                                   originalFileName: String): String {
		return withIOContext {
			var sanitizedFileName = sanitizeFileNameExtreme(originalFileName)
			var index = 1
			val regex = Regex("^(\\d+)_")

			while (fileDirectory.findFile(sanitizedFileName) != null) {
				val matchResult = regex.find(sanitizedFileName)
				if (matchResult != null) {
					val currentIndex = matchResult.groupValues[1].toInt()
					sanitizedFileName = sanitizedFileName.replaceFirst(regex, "")
					index = currentIndex + 1
				}
				sanitizedFileName = "${index}_$sanitizedFileName"
				index++
			}

			return@withIOContext sanitizedFileName
		}
	}

	@JvmStatic
	suspend fun findFileStartingWith(internalDir: File,
	                                 namePrefix: String): File? {
		return withIOContext {
			val result = internalDir.listFiles()?.find {
				it.isFile && it.name.startsWith(namePrefix)
			}
			return@withIOContext result
		}
	}

	@JvmStatic
	suspend fun makeDirectory(parentFolder: DocumentFile?,
	                          folderName: String): DocumentFile? {
		return withIOContext {
			val newDirectory = parentFolder?.createDirectory(folderName)
			return@withIOContext newDirectory
		}
	}

	@JvmStatic
	suspend fun getMimeType(fileName: String): String? {
		return withIOContext {
			val extension = getFileExtension(fileName)?.lowercase(Locale.getDefault())
			val mimeType = extension?.let {
				MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
			} ?: run {
				val uri = "content://$extension".toUri()
				INSTANCE.contentResolver.getType(uri)
			}
			return@withIOContext mimeType
		}
	}

	@JvmStatic
	suspend fun getFileExtension(fileName: String): String? {
		return fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
	}

	@JvmStatic
	suspend fun DocumentFile.isFileType(extensions: Array<String>): Boolean {
		return endsWithExtension(name, extensions)
	}

	@JvmStatic
	suspend fun isAudio(file: DocumentFile): Boolean {
		return file.isFileType(MUSIC_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isArchive(file: DocumentFile): Boolean {
		return file.isFileType(ARCHIVE_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isProgram(file: DocumentFile): Boolean {
		return file.isFileType(PROGRAM_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isVideo(file: DocumentFile): Boolean {
		return file.isFileType(VIDEO_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isDocument(file: DocumentFile): Boolean {
		return file.isFileType(DOCUMENT_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isImage(file: DocumentFile): Boolean {
		return file.isFileType(IMAGE_EXTENSIONS)
	}

	@JvmStatic
	suspend fun getFileType(file: DocumentFile): String {
		return getFileType(file.name)
	}

	@JvmStatic
	suspend fun getFileType(fileName: String?): String {
		return when {
			isAudioByName(fileName) -> getText(R.string.title_sounds)
			isArchiveByName(fileName) -> getText(R.string.title_archives)
			isProgramByName(fileName) -> getText(R.string.title_programs)
			isVideoByName(fileName) -> getText(R.string.title_videos)
			isDocumentByName(fileName) -> getText(R.string.title_documents)
			isImageByName(fileName) -> getText(R.string.title_images)
			else -> getText(R.string.title_others)
		}
	}

	@JvmStatic
	suspend fun addToMediaStore(file: File) {
		withIOContext {
			try {
				val fileUri = Uri.fromFile(file)
				val action = ACTION_MEDIA_SCANNER_SCAN_FILE
				val mediaScanIntent = Intent(action).apply {
					data = fileUri
				}
				INSTANCE.sendBroadcast(mediaScanIntent)
			} catch (error: Exception) {
				error.printStackTrace()
			}
		}
	}

	@JvmStatic
	suspend fun endsWithExtension(fileName: String?, extensions: Array<String>): Boolean {
		return extensions.any {
			fileName?.lowercase(Locale.getDefault())?.endsWith(".$it") == true
		}
	}

	@JvmStatic
	suspend fun isAudioByName(name: String?): Boolean {
		return endsWithExtension(name, MUSIC_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isArchiveByName(name: String?): Boolean {
		return endsWithExtension(name, ARCHIVE_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isProgramByName(name: String?): Boolean {
		return endsWithExtension(name, PROGRAM_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isVideoByName(name: String?): Boolean {
		return endsWithExtension(name, VIDEO_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isDocumentByName(name: String?): Boolean {
		return endsWithExtension(name, DOCUMENT_EXTENSIONS)
	}

	@JvmStatic
	suspend fun isImageByName(name: String?): Boolean {
		return endsWithExtension(name, IMAGE_EXTENSIONS)
	}

	@JvmStatic
	suspend fun getFileNameWithoutExtension(fileName: String): String {
		return withIOContext {
			try {
				val dotIndex = fileName.lastIndexOf('.')
				if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
			} catch (error: Exception) {
				error.printStackTrace()
				fileName
			}
		}
	}

	@JvmStatic
	suspend fun getFileSha256(file: File): String {
		return withIOContext {
			val digest = MessageDigest.getInstance("SHA-256")
			FileInputStream(file).use { input ->
				val buffer = ByteArray(8192)
				var bytesRead: Int
				while (input.read(buffer).also { bytesRead = it } != -1) {
					digest.update(buffer, 0, bytesRead)
				}
			}
			return@withIOContext digest.digest().joinToString("") {
				"%02x".format(it)
			}
		}
	}
}
