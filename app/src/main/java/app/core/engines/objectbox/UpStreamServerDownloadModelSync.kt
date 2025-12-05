package app.core.engines.objectbox

import app.core.*
import app.core.bases.*
import app.core.engines.downloader.*
import app.ui.main.*
import com.parse.*
import com.parse.ParseInstallation.*
import lib.process.*
import org.json.*
import java.util.*

class UpStreamServerDownloadModelSync {
	
	/**
	 * Logger instance for this class, used for detailed operation logging.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	fun uploadUserDetailsToSever(baseActivity: BaseActivity) {
		ThreadsUtility.executeInBackground(codeBlock = {
			AIOApp.downloadSystem.prefetchedEntireDownloadModels.ifEmpty {
				DownloadModelsDBManager.getAllDownloadsWithRelationsAssembled()
			}.forEachIndexed { index, dataModel ->
				val convertClassToJSON = dataModel.convertClassToJSON()
				logger.d("Index:$index, DownloadModel [${dataModel.downloadId}]: $convertClassToJSON")
				uploadJsonToParse(dataModel.javaClass.simpleName, convertClassToJSON)
			}
		})
	}
	
	fun downStreamSyncFromServer(baseActivity: BaseActivity) {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 5000,
			codeBlock = {
				try {
					val parseQuery = ParseQuery.getQuery<ParseObject>("DownloadDataModel")
					parseQuery.whereEqualTo("installation_id", getCurrentInstallation().installationId)
					parseQuery.orderByDescending("createdAt")
					val results = parseQuery.find()
					
					if (results.isNotEmpty()) {
						logger.d("Found ${results.size} download model records from server")
						for (parseObject in results) {
							val rawJson = parseObject.getString("raw_json")
							
							if (!rawJson.isNullOrEmpty()) {
								DownloadDataModel.convertToClassFromJSON(rawJson)?.let {
									it.updateInStorage()
									logger.d("Restored DownloadModel ID${it.id}, DownloadId${it.downloadId}")
								}
								
								val createdAt = parseObject.createdAt
								logger.d("DownloadModel restored from record created at: $createdAt")
							}
						}
						
					} else {
						logger.d("No DownloadModel found on server for this installation")
					}
					
					if (baseActivity is MotherActivity) {
						baseActivity.downloadFragment
							?.finishedTasksFragment
							?.finishedTasksListAdapter
							?.notifyDataSetChangedOnSort(true)
					}
				} catch (error: Exception) {
					logger.d("downStreamSyncFromServer failed: ${error.message}")
				}
			}
		)
	}
	
	fun uploadJsonToParse(
		tableName: String,
		jsonString: String,
		additionalData: Map<String, Any> = emptyMap(),
		storeRawJson: Boolean = true,
		rawJsonColumnName: String = "raw_json"
	) {
		if (!AIOApp.IS_CLOUD_BACKUP_ENABLED) return
		
		try {
			val jsonObject = JSONObject(jsonString)
			val cloudTable = ParseObject(tableName)
			
			// 1. Store the entire JSON string in a column if requested
			if (storeRawJson) {
				cloudTable.put(rawJsonColumnName, jsonString)
			}
			
			// Add the installation ID
			cloudTable.put("installation_id", getCurrentInstallation().installationId)
			
			// List of reserved Parse field names
			val reservedFields = listOf(
				"objectId", "id", "ACL", "createdAt", "updatedAt",
				"authData", "sessionToken", "_rperm", "_wperm"
			)
			
			// Add all fields from JSON
			val iterator = jsonObject.keys()
			while (iterator.hasNext()) {
				val key = iterator.next()
				val value = jsonObject.get(key)
				
				// Skip reserved fields or rename them
				when {
					reservedFields.contains(key) -> {
						// Rename "id" to "original_id" or skip
						if (key == "id") {
							cloudTable.put("original_id", value)
						}
						// For other reserved fields, you can either skip or rename
						// else -> skip
					}
					
					else -> {
						// Only add non-null and non-empty values
						if (value != null && value != JSONObject.NULL) {
							cloudTable.put(key, value)
						}
					}
				}
			}
			
			// Add any additional data (like device info, etc.)
			for ((key, value) in additionalData) {
				cloudTable.put(key, value)
			}
			
			// Add timestamp for when this was uploaded
			cloudTable.put("uploaded_at", Date())
			
			// Save in background
			cloudTable.saveInBackground {
				if (it != null) {
					logger.d("uploadJsonToParse failed: ${it.message}")
				} else {
					logger.d("uploadJsonToParse succeeded for table: $tableName")
				}
			}
			
		} catch (error: Exception) {
			logger.d("uploadJsonToParse failed: ${error.message}")
		}
	}
}