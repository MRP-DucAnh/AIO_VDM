package app.core.engines.user_profile

import android.content.Context.*
import app.core.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioDSLJsonInstance
import app.core.AIOApp.Companion.aioUserProfile
import app.core.engines.fst_serializer.FSTBuilder.fstConfig
import com.anggrayudi.storage.file.*
import com.dslplatform.json.*
import io.objectbox.annotation.*
import lib.files.FileSystemUtility.readStringFromInternalStorage
import lib.files.FileSystemUtility.saveStringToInternalStorage
import lib.process.*
import java.io.*
import kotlin.jvm.Transient

/**
 * Represents a user's profile data within the application.
 *
 * This class is designed to be a central repository for user-specific information,
 * such as authentication status and personal details. It supports multiple persistence
 * mechanisms to ensure data integrity and backward compatibility.
 *
 * The data is persisted in three ways:
 * 1.  **ObjectBox Database:** As an `@Entity`, it is stored in a structured, efficient database.
 * 2.  **JSON File:** A human-readable JSON representation (`user_profile.json`) is saved
 *     in the app's internal storage for easy debugging and potential cross-platform use.
 * 3.  **Binary File:** A serialized binary format (`user_profile.dat`) is used for fast
 *     read/write operations, leveraging FST serialization.
 *
 * The class includes logic to read from older formats (binary, then JSON) and migrate
 * the data to the current persistence layers upon initialization.
 *
 * @property id The unique identifier for the user profile entry in the local ObjectBox database.
 * @property uniqueUserServerId A unique ID assigned by the server to identify the user across devices.
 *           Null if not available.
 * @property isUserCurrentlyLoggedIn A flag indicating whether the user is currently signed into their account.
 * @property userFullName The full name of the user. Null if not provided.
 * @property userEmailAddress The email address associated with the user's account. Null if not provided.
 * @property userPhoneNumber The phone number associated with the user's account. Null if not provided.
 */
@CompiledJson
@Entity
class AIOUserProfile : Serializable {
	
	@Transient
	private val logger = LogHelperUtils.from(javaClass)
	
	@Id
	@JvmField
	@JsonAttribute(name = "id")
	var id: Long = 0L
	
	@JvmField
	@JsonAttribute(name = "uniqueUserServerId")
	var uniqueUserServerId: String? = null
	
	@JvmField
	@JsonAttribute(name = "isUserCurrentlyLoggedIn")
	var isUserCurrentlyLoggedIn: Boolean = false
	
	@JvmField
	@JsonAttribute(name = "userFullName")
	var userFullName: String? = null
	
	@JvmField
	@JsonAttribute(name = "userEmailAddress")
	var userEmailAddress: String? = null
	
	@JvmField
	@JsonAttribute(name = "userPhoneNumber")
	var userPhoneNumber: String? = null
	
	fun readObjectFromStorage() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 1200,
			codeBlock = {
				initializeLegacyDataParser()
			})
	}
	
	private fun initializeLegacyDataParser() {
		try {
			var isBinaryFileValid = false
			val internalDir = AIOApp.internalDataFolder
			val userProfileBinaryDataFile = internalDir.findFile(USER_PROFILE_FILE_NAME_BINARY)
			if (userProfileBinaryDataFile != null && userProfileBinaryDataFile.exists()) {
				logger.d("Found binary user profile file, attempting to load")
				val absolutePath = userProfileBinaryDataFile.getAbsolutePath(INSTANCE)
				val objectInMemory = loadFromBinary(File(absolutePath))
				
				if (objectInMemory != null) {
					logger.d("Successfully loaded user profile from binary format")
					aioUserProfile = objectInMemory
					aioUserProfile.updateInStorage()
					isBinaryFileValid = true
				} else {
					logger.d("Failed to load user profile from binary format")
				}
			}
			
			if (!isBinaryFileValid) {
				logger.d("Attempting to load user profile from JSON format")
				
				readStringFromInternalStorage(USER_PROFILE_FILE_NAME_JSON).let { jsonString ->
					if (jsonString.isNotEmpty()) {
						convertJSONStringToClass(jsonString = jsonString).let {
							logger.d("Successfully loaded user profile from JSON format")
							aioUserProfile = it
							aioUserProfile.updateInStorage()
						}
					} else {
						logger.d("No JSON user profile found or file empty")
					}
				}
			}
			
		} catch (error: Exception) {
			logger.e("Error reading user profile from storage: ${error.message}", error)
		}
	}
	
	fun updateInStorage() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 100, codeBlock = {
			try {
				logger.d("Updating user profile in storage")
				saveToBinary(fileName = USER_PROFILE_FILE_NAME_BINARY)
				saveStringToInternalStorage(
					fileName = USER_PROFILE_FILE_NAME_JSON,
					data = convertClassToJSON()
				)
				AIOUserProfileDBManager.saveUserProfileInDB(userProfile = this)
				logger.d("User profile successfully updated in storage")
			} catch (error: Exception) {
				logger.e("Error updating user profile in storage: ${error.message}", error)
			}
		})
	}
	
	@Synchronized
	private fun saveToBinary(fileName: String) {
		try {
			logger.d("Saving user profile to binary file: $fileName")
			val fileOutputStream = INSTANCE.openFileOutput(fileName, MODE_PRIVATE)
			fileOutputStream.use { fos ->
				val bytes = fstConfig.asByteArray(this)
				fos.write(bytes)
				logger.d("Binary user profile saved successfully")
			}
		} catch (error: Exception) {
			logger.e("Error saving binary user profile: ${error.message}", error)
		}
	}
	
	private fun loadFromBinary(userProfileBinaryFile: File): AIOUserProfile? {
		if (!userProfileBinaryFile.exists()) {
			logger.d("Binary user profile file does not exist")
			return null
		}
		
		return try {
			logger.d("Loading user profile from binary file")
			val bytes = userProfileBinaryFile.readBytes()
			fstConfig.asObject(bytes).apply {
				logger.d("Successfully loaded user profile from binary file")
			} as AIOUserProfile
		} catch (error: Exception) {
			logger.e("Error loading binary user profile: ${error.message}", error)
			userProfileBinaryFile.delete()
			null
		}
	}
	
	fun convertClassToJSON(): String {
		val jsonOutputStream = ByteArrayOutputStream(16 * 1024)
		logger.d("Converting user profile to JSON")
		jsonOutputStream.reset()
		aioDSLJsonInstance.serialize(this, jsonOutputStream)
		return jsonOutputStream.toString(Charsets.UTF_8.name())
	}
	
	companion object {
		
		private val logger = LogHelperUtils.from(AIOUserProfile::class.java)
		const val USER_PROFILE_FILE_NAME_JSON: String = "user_profile.json"
		const val USER_PROFILE_FILE_NAME_BINARY: String = "user_profile.dat"
		
		@JvmStatic
		fun convertJSONStringToClass(jsonString: String): AIOUserProfile {
			logger.d("Converting JSON to user profile object")
			val inputStream = ByteArrayInputStream(jsonString.encodeToByteArray())
			val loadedUserProfile: AIOUserProfile = aioDSLJsonInstance
				.deserialize(AIOUserProfile::class.java, inputStream) ?: AIOUserProfile()
			return loadedUserProfile
		}
	}
}