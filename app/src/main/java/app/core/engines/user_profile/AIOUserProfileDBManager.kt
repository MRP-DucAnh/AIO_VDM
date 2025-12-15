package app.core.engines.user_profile

import app.core.engines.objectbox.*
import app.core.engines.settings.AIOSettingsDBManager.getSettingsObjectBox
import io.objectbox.*
import lib.process.*

object AIOUserProfileDBManager {
	
	private val logger = LogHelperUtils.from(javaClass)
	
	@JvmStatic
	fun getGlobalObjectBoxStore(): BoxStore {
		return ObjectBoxManager.getBoxStore()
	}
	
	@JvmStatic
	fun getUserProfileObjectBox(): Box<AIOUserProfile> {
		return getGlobalObjectBoxStore().boxFor(AIOUserProfile::class.java)
	}
	
	@JvmStatic
	@Synchronized
	fun saveUserProfileInDB(userProfile: AIOUserProfile) {
		try {
			getUserProfileObjectBox().put(userProfile)
			logger.d("User profile saved successfully to database id:${userProfile.id}")
		} catch (error: Exception) {
			logger.e("Error saving user profile to: ${error.message}", error)
		}
	}
	
	@JvmStatic
	fun loadSettingsFromDB(): AIOUserProfile {
		return try {
			val userProfileBox = getUserProfileObjectBox()
			var appUserProfile = userProfileBox.query().build().findFirst()
			
			if (appUserProfile == null) {
				logger.d("No user profile found in database, creating default user profile")
				appUserProfile = createDefaultUserProfileObject()
				saveUserProfileInDB(appUserProfile)
			} else {
				logger.d("User profile loaded successfully from database, id: ${appUserProfile.id}")
			}
			
			appUserProfile
		} catch (error: Exception) {
			logger.e("Error loading user profile from database: ${error.message}", error)
			try {
				createDefaultUserProfileObject().also { savedUserProfile ->
					saveUserProfileInDB(savedUserProfile)
					logger.d("Recovery: Default user profile created and saved after load error")
				}
			} catch (saveError: Exception) {
				logger.e("Failed to save default user profile after error: ${saveError.message}", saveError)
				AIOUserProfile().also { logger.d("Using in-memory default profile as final fallback") }
			}
		}
	}
	
	@JvmStatic
	private fun createDefaultUserProfileObject(): AIOUserProfile {
		return AIOUserProfile().apply(AIOUserProfile::readObjectFromStorage).also {
			logger.d("Default user profile created with legacy data migration")
		}
	}
	
	@JvmStatic
	fun doesUserProfileRecordExist(): Boolean {
		return try {
			val appUserProfile = getUserProfileObjectBox().query().build().findFirst()
			appUserProfile != null
		} catch (error: Exception) {
			logger.e("Error checking user profile existence: ${error.message}", error)
			false
		}
	}
	
	@JvmStatic
	fun deleteUserProfileFromDB() {
		try {
			val appUserProfile = getUserProfileObjectBox()
				.query().build().findFirst() ?: return
			getSettingsObjectBox().remove(appUserProfile.id)
			logger.d("User profile cleared from ObjectBox database")
		} catch (error: Exception) {
			logger.e("Error clearing user profile: ${error.message}", error)
		}
	}
}