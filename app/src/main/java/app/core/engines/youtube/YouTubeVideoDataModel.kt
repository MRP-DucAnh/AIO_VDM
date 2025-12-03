package app.core.engines.youtube

import com.dslplatform.json.*
import io.objectbox.annotation.*
import lib.process.*
import java.io.*

@CompiledJson
@Entity
class YouTubeVideoDataModel(
	@Id
	@JvmField
	@param:JsonAttribute(name = "id")
	var id: Long = 0L,
	
	@JvmField
	@param:JsonAttribute(name = "lastModifiedTimeDate")
	var lastModifiedTimeDate: Long = 0L,
	
	@JvmField
	@param:JsonAttribute(name = "videoUrl")
	var videoUrl: String = "",
	
	@JvmField
	@param:JsonAttribute(name = "videoTitle")
	var videoTitle: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoThumbnailUrl")
	var videoThumbnailUrl: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoDescription")
	var videoDescription: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoDuration")
	var videoDuration: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoViewsCount")
	var videoViewsCount: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoLikesCount")
	var videoLikesCount: String? = null,
	
	@JvmField
	@param:JsonAttribute(name = "videoChannelName")
	var videoChannelName: String? = null,
) : Serializable {
	
	private val logger = LogHelperUtils.from(javaClass)
	
	fun updateInStorage() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 500,
			codeBlock = {
				YTVideosModelsDBManager.saveYouTubeVideoModelsInDB(
					youTubeVideoModel = this,
					listener = object : YTVideosModelsDBManager.OnSaveResultListener {
						override fun onSuccess(savedId: Long) {
							logger.d("YouTube video model saved to storage successfully.")
						}
						
						override fun onError(error: Exception) {
							logger.d("Failed to save YouTube video model to storage.")
						}
					}
				)
			}
		)
	}
	
	fun clearFromStorage() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 500,
			codeBlock = {
				val isDeleted = YTVideosModelsDBManager.deleteYouTubeVideoById(id)
				logger.d(
					"Deletion of YouTube video model from storage was " +
						"${if (isDeleted) "successful" else "failed"}."
				)
			}
		)
	}
}