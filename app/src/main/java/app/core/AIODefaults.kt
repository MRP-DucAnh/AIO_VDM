package app.core

const val APP_DEFAULT_NAME = "TubeAIO"
const val APP_DEFAULT_SHORT_NAME = "AIO"
const val APP_DEFAULT_FULL_NAME = "TubeAIO Downloader"
const val APP_DOWNLOADS = "$APP_DEFAULT_SHORT_NAME Downloads"
const val APP_DOWNLOADS_PAH = "/storage/emulated/0/Download/$APP_DEFAULT_SHORT_NAME Downloads"

const val APP_FULL_NUMBERS = "0123456789"
const val APP_FULL_APPLETS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

val APP_DEFAULT_MOBILE_AGENTS = listOf(
	"Mozilla/5.0 (iPhone; CPU iPhone OS 9_3_5 like Mac OS X) AppleWebKit/601.1.46 (KHTML," +
		" like Gecko) Version/9.0 Mobile/13G36 Safari/601.1",
	"Mozilla/5.0 (Linux; Android 4.4.2; Nexus 5 Build/KOT49H) AppleWebKit/537.36 (KHTML, " +
		"like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36",
	"Mozilla/5.0 (Linux; U; Android 2.3.6; en-us; GT-I9000 Build/GINGERBREAD) AppleWebKit/533.1 " +
		"(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"
)

const val APP_ALL_MEDIA_TYPES = "text/html,application/xhtml+xml,application/xml;q=0.9," +
	"image/webp,image/apng,image/avif,image/jpeg,image/png,image/gif,image/svg+xml,image/*," +
	"*/*;q=0.8"

const val APP_DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
const val APP_DEFAULT_TIMESTAMP_PATTERN = "%02d:%02d:%02d"
const val APP_DEFAULT_DURATION_PATTERN = "%d:%02d:%02d"
const val APP_DEFAULT_TIME_PATTERN = "%02d:%02d"

const val APP_TIME_FORMAT_12_HOUR = "hh:mm a"
const val APP_TIME_FORMAT_24_HOUR = "HH:mm"
const val APP_TIME_FORMAT_DAY = "d"
const val APP_TIME_FORMAT_MONTH = "MMM"
const val APP_TIME_EMPTY = "00:00"
