package app.core.bases.interfaces

interface PermissionsResult {

	fun onPermissionResultFound(
		isGranted: Boolean,
		grantedList: List<String>?,
		deniedList: List<String>?
	)
}