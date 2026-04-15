package lib.networks;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.TELEPHONY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.webkit.MimeTypeMap.getSingleton;
import static app.core.AIOApp.INSTANCE;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import lib.process.LogHelperUtils;

public class NetworkUtility {

	private static final LogHelperUtils logger =
		LogHelperUtils.from(NetworkUtility.class);

	public static boolean isNetworkAvailable() {
		Context context = INSTANCE;
		ConnectivityManager connectivityManager = (ConnectivityManager)
			context.getSystemService(CONNECTIVITY_SERVICE);

		if (connectivityManager == null) return false;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			NetworkCapabilities capabilities = connectivityManager.
				getNetworkCapabilities(connectivityManager.getActiveNetwork());

			return capabilities != null &&
				(capabilities.hasTransport(TRANSPORT_WIFI) ||
					capabilities.hasTransport(TRANSPORT_CELLULAR));
		} else {
			NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
			return activeNetworkInfo != null && activeNetworkInfo.isConnected();
		}
	}

	public static boolean isWifiEnabled() {
		Object wifiService = INSTANCE.getSystemService(WIFI_SERVICE);
		WifiManager wifiManager = (WifiManager) wifiService;
		return wifiManager.isWifiEnabled();
	}

	@Nullable
	public static String getMimeTypeFromUrl(@NonNull String url) {
		String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (fileExtension != null)
			return getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
		return null;
	}

	@NonNull
	public static String getOriginalUrlFromRedirectedUrl
		(@NonNull String fileURL) throws IOException {
		HttpURLConnection connection = (HttpURLConnection)
			new URL(fileURL).openConnection();
		connection.setInstanceFollowRedirects(false);
		connection.connect();
		int responseCode = connection.getResponseCode();
		if (responseCode >= 300 && responseCode < 400) {
			String originalUrl = connection.getHeaderField("Location");
			if (originalUrl != null) {
				return originalUrl;
			}
		}
		return fileURL;
	}

	@NonNull
	public static String getServiceProvider() {
		Object telephonyService = INSTANCE.getSystemService(TELEPHONY_SERVICE);
		TelephonyManager manager = (TelephonyManager) telephonyService;
		if (manager != null) return manager.getNetworkOperatorName();
		else return "";
	}

	public static boolean isUrlAccessible(@NonNull String urlString) {
		try {
			URLConnection urlConnection = new URL(urlString).openConnection();
			HttpURLConnection connection = (HttpURLConnection) urlConnection;
			connection.setRequestMethod("HEAD");
			int responseCode = connection.getResponseCode();
			return responseCode == HttpURLConnection.HTTP_OK;
		} catch (Throwable error) {
			logger.e("Error found while checking URL accessibility:", error);
			return false;
		}
	}

	@Deprecated
	@NonNull
	public static String normalizeUrl(@NonNull String url) {
		if (!url.endsWith("/") && URLUtil.isValidUrl(url)) {
			return url.replaceAll("/$", "") + "/";
		}
		return url;
	}

	@NonNull
	public static String[] extractUniqueDomains(@NonNull String[] urls) {
		List<String> uniqueDomains = new ArrayList<>();
		for (String url : urls) {
			String domain = Uri.parse(url).getHost();
			if (domain != null && !uniqueDomains.contains(domain)) {
				uniqueDomains.add(domain);
			}
		}
		return uniqueDomains.toArray(new String[0]);
	}
}