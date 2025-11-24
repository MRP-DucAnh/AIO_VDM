package lib.files

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lib.process.LogHelperUtils
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class VideoToAudioConverter(coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {

	private val logger = LogHelperUtils.from(javaClass)
	private var isProcessCancelledByUser = AtomicBoolean(false)

	fun extractAudio(inputFile: String, outputFile: String, listener: ConversionListener) {
		val weakListener = WeakReference(listener)
		weakListener.get()?.let { safeListener ->
			try {
				logger.d("Starting audio extraction from video: $inputFile")

				val extractor = MediaExtractor()
				extractor.setDataSource(inputFile)

				var audioTrackIndex = -1
				var format: MediaFormat? = null

				logger.d("Scanning tracks in video file...")
				for (index in 0 until extractor.trackCount) {
					format = extractor.getTrackFormat(index)
					val mime = format.getString(MediaFormat.KEY_MIME)
					logger.d("Checking track $index: MIME = $mime")

					if (mime?.startsWith("audio/") == true) {
						audioTrackIndex = index
						extractor.selectTrack(index)
						logger.d("Selected audio track index: $audioTrackIndex")
						break
					}
				}

				if (audioTrackIndex == -1 || format == null) {
					val errorMsg = "No audio track found in video file: $inputFile"
					logger.d(errorMsg)
					safeListener.onFailure(errorMsg)
					extractor.release()
					return
				}

				logger.d("Initializing MediaMuxer with output file: $outputFile")
				val muxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
				val newTrackIndex = muxer.addTrack(format)
				muxer.start()

				val buffer = ByteBuffer.allocate(4096)
				val bufferInfo = MediaCodec.BufferInfo()

				val fileSize = File(inputFile).length().toFloat()
				var extractedSize = 0L

				logger.d("Starting audio sample extraction loop...")

				while (!isProcessCancelledByUser.load()) {
					buffer.clear()

					val sampleSize = extractor.readSampleData(buffer, 0)
					if (sampleSize < 0) {
						logger.d("End of stream reached, sampleSize: $sampleSize")
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
					safeListener.onProgress(progress)
				}

				if (isProcessCancelledByUser.load()) {
					logger.d("Audio extraction cancelled by user")
					safeListener.onFailure("Audio extraction cancelled")
					muxer.stop()
					muxer.release()
					extractor.release()
					return
				}

				muxer.stop()
				muxer.release()
				extractor.release()

				logger.d("Audio extraction completed successfully. Output: $outputFile")
				safeListener.onSuccess(outputFile)

			} catch (error: Exception) {
				val errorMsg = "Audio extraction failed: ${error.message}"
				logger.e("$errorMsg. Error: $error")
				safeListener.onFailure(errorMsg)
			}
		}
	}

	fun cancel() {
		logger.d("Cancellation requested for audio extraction")
		isProcessCancelledByUser.store(true)
	}

	interface ConversionListener {
		fun onProgress(progress: Int)
		fun onSuccess(outputFile: String)
		fun onFailure(errorMessage: String)
	}
}
