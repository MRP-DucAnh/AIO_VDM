package lib.files

import android.media.*
import android.media.MediaMuxer.OutputFormat.*
import lib.process.*
import java.io.*
import java.nio.*
import kotlin.concurrent.atomics.*

@OptIn(ExperimentalAtomicApi::class)
class VideoToAudioConverter {

	private val logger = LogHelperUtils.from(javaClass)
	private var isProcessCancelledByUser = AtomicBoolean(false)
	private var isMuxerStarted = false

	suspend fun extractAudio(inputFile: String, outputFile: String,
	                         listener: ConversionListener) {
		withIOContext {
			var extractor: MediaExtractor? = null
			var muxer: MediaMuxer? = null

			try {
				extractor = MediaExtractor()
				extractor.setDataSource(inputFile)

				var audioTrackIndex = -1
				var format: MediaFormat? = null

				for (index in 0 until extractor.trackCount) {
					format = extractor.getTrackFormat(index)
					val mime = format.getString(MediaFormat.KEY_MIME)
					if (mime?.startsWith("audio/") == true) {
						audioTrackIndex = index
						extractor.selectTrack(index)
						break
					}
				}

				if (audioTrackIndex == -1 || format == null) {
					val errorMsg = "No audio track found in video file: $inputFile"
					logger.d(errorMsg)
					withMainContext { listener.onFailure(errorMsg) }
					return@withIOContext
				}

				val mime = format.getString(MediaFormat.KEY_MIME)
				val (muxerFormat, _) = when (mime) {
					"audio/aac", "audio/mp4a-latm" -> MUXER_OUTPUT_MPEG_4 to ".m4a"
					"audio/opus", "audio/vorbis" -> MUXER_OUTPUT_WEBM to ".mka"
					else -> {
						val errorMsg = "Unsupported audio MIME type extraction: $mime"
						logger.e(errorMsg)
						withMainContext { listener.onFailure(errorMsg) }
						return@withIOContext
					}
				}

				muxer = MediaMuxer(outputFile, muxerFormat)
				val newTrackIndex = muxer.addTrack(format)
				muxer.start()
				isMuxerStarted = true

				val buffer = ByteBuffer.allocate(4096)
				val bufferInfo = MediaCodec.BufferInfo()

				val fileSize = File(inputFile).length().toFloat()
				var extractedSize = 0L

				while (!isProcessCancelledByUser.load()) {
					buffer.clear()

					val sampleSize = extractor.readSampleData(buffer, 0)
					if (sampleSize < 0) {
						break
					}

					bufferInfo.offset = 0
					bufferInfo.size = sampleSize
					bufferInfo.presentationTimeUs = extractor.sampleTime
					bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

					muxer.writeSampleData(newTrackIndex, buffer, bufferInfo)

					extractor.advance()

					extractedSize += sampleSize
					val progress = ((extractedSize / fileSize) * 100).toInt()
					withMainContext { listener.onProgress(progress) }
				}

				if (isProcessCancelledByUser.load()) {
					withMainContext { listener.onFailure("Audio extraction cancelled") }
					return@withIOContext
				}

				muxer.stop()
				isMuxerStarted = false
				withMainContext { listener.onSuccess(outputFile) }

			} catch (error: Exception) {
				val errorMsg = "Audio extraction failed: ${error.message}"
				logger.e("$errorMsg. Error: $error")
				withMainContext { listener.onFailure(errorMsg) }

			} finally {
				if (isMuxerStarted) {
					try {
						muxer?.stop()
					} catch (error: Exception) {
						error.printStackTrace()
					}
				}

				muxer?.release()
				extractor?.release()
				isMuxerStarted = false
			}
		}
	}

	suspend fun cancel() {
		isProcessCancelledByUser.store(true)
	}

	interface ConversionListener {
		suspend fun onProgress(progress: Int)
		suspend fun onSuccess(outputFile: String)
		suspend fun onFailure(errorMessage: String)
	}
}
