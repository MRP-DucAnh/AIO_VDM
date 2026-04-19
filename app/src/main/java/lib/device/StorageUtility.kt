package lib.device

import android.os.*
import android.os.Environment.*
import lib.process.*

object StorageUtility {

	@JvmStatic
	suspend fun getTotalStorageSpace(): Long {
		return withIOContext {
			val path = getDataDirectory()
			val stat = StatFs(path.path)
			val blockSize = stat.blockSizeLong
			val totalBlocks = stat.blockCountLong
			return@withIOContext totalBlocks * blockSize
		}
	}

	@JvmStatic
	suspend fun getFreeStorageSpace(): Long {
		return withIOContext {
			val path = getDataDirectory()
			val stat = StatFs(path.path)
			val blockSize = stat.blockSizeLong
			val availableBlocks = stat.availableBlocksLong
			return@withIOContext availableBlocks * blockSize
		}
	}

	@JvmStatic
	suspend fun getFreeStoragePercentage(): Float {
		return withIOContext {
			val totalSpace = getTotalStorageSpace()
			val freeSpace = getFreeStorageSpace()
			val freePercentage = (freeSpace.toFloat() / totalSpace) * 100
			return@withIOContext freePercentage
		}
	}

	@JvmStatic
	suspend fun getTotalExternalStorageSpace(): Long {
		return withIOContext {
			if (getExternalStorageState() == MEDIA_MOUNTED) {
				val path = getExternalStorageDirectory()
				val stat = StatFs(path.path)
				val blockSize = stat.blockSizeLong
				val totalBlocks = stat.blockCountLong
				return@withIOContext totalBlocks * blockSize
			} else {
				0
			}
		}
	}

	@JvmStatic
	suspend fun getFreeExternalStorageSpace(): Long {
		return withIOContext {
			if (getExternalStorageState() == MEDIA_MOUNTED) {
				val path = getExternalStorageDirectory()
				val stat = StatFs(path.path)
				val blockSize = stat.blockSizeLong
				val availableBlocks = stat.availableBlocksLong
				return@withIOContext availableBlocks * blockSize
			} else {
				0
			}
		}
	}

	@JvmStatic
	suspend fun getFreeExternalStoragePercentage(): Float {
		return withIOContext {
			val totalSpace = getTotalExternalStorageSpace()
			val freeSpace = getFreeExternalStorageSpace()
			val freePercentage = if (totalSpace != 0L) {
				(freeSpace.toFloat() / totalSpace) * 100
			} else {
				0f
			}
			return@withIOContext freePercentage
		}
	}
}
