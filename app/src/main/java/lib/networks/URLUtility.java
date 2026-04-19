package lib.networks;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.os.Build;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lib.process.LogHelperUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class URLUtility {

	private static final LogHelperUtils logger =
		LogHelperUtils.from(URLUtility.class);

	public static final String CONTENT_DISPOSITION = "Content-Disposition";
	public static final String ACCEPT_RANGES = "Accept-Ranges";
	public static final String BYTES = "bytes";
	public static final String LAST_MODIFIED = "Last-Modified";
	public static final String E_TAG = "ETag";
	public static final String HEAD = "HEAD";
	public static final String CONTENT_LENGTH = "Content-Length";

	public static boolean isValidURL(@Nullable String url) {
		if (url == null || url.isEmpty()) return false;
		try {
			new URL(url);
			return true;
		} catch (Throwable error) {
			return false;
		}
	}

	@Nullable
	public static String getFileNameFromURL(@NonNull String urlString) {
		try {
			URL url = new URL(urlString);
			String filePath = url.getPath();
			int lastSlashIndex = filePath.lastIndexOf('/');
			if (lastSlashIndex == -1) return filePath;
			else return filePath.substring(lastSlashIndex + 1);
		} catch (Exception error) {
			logger.e("Error found while getting file name from url:", error);
			return null;
		}
	}

	public static boolean isValidDomain(String domain) {
		String domainRegex = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$";
		return domain.matches(domainRegex);
	}

	@Nullable
	public static String ensureHttps(@NonNull String url) {
		if (!isValidDomain(url)) return null;
		String nakedDomain = url.replaceFirst("^(https?://)?(www\\.)?", "");
		if (!nakedDomain.startsWith("https://")) {
			nakedDomain = "https://" + nakedDomain;
		}
		return nakedDomain;
	}

	public static boolean isUrlAccessible(@NonNull String urlString) {
		try {
			HttpURLConnection connection = (HttpURLConnection)
				new URL(urlString).openConnection();
			connection.setRequestMethod(HEAD);
			int responseCode = connection.getResponseCode();
			return responseCode == HttpURLConnection.HTTP_OK;
		} catch (Throwable error) {
			logger.e("Error found while checking url accessibility:", error);
			return false;
		}
	}

	@NonNull
	public static String[] extractLinks(@NonNull String text) {
		List<String> links = new ArrayList<>();
		Pattern pattern = Patterns.WEB_URL;
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			String url = matcher.group();
			links.add(url);
		}
		return links.toArray(new String[0]);
	}

	public static long getFileSizeFromUrl(@NonNull URL url) {
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(HEAD);
			connection.connect();
			return connection.getContentLength();
		} catch (IOException error) {
			logger.e("Error found while getting file size form url:", error);
			return -1;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	public static long getFileSizeFromURL_OkHttp(@NonNull URL url) {
		try {
			OkHttpClient client = new OkHttpClient.Builder()
				.followRedirects(true).followSslRedirects(true).build();
			Request request = new Request.Builder().url(url).head().build();
			try (Response response = client.newCall(request).execute()) {
				if (response.isSuccessful()) {
					String contentLength = response.header(CONTENT_LENGTH);
					if (contentLength != null) {
						return Long.parseLong(contentLength);
					} else {
						throw new IOException("Content-Length header is missing");
					}
				} else {
					throw new IOException("Failed to fetch file size: "
						+ response.message());
				}
			}
		} catch (Exception error) {
			logger.e("Error getting file name from url:", error);
			return -1;
		}
	}

	public static boolean supportsMultipartDownload(
		@NonNull String fileUrl) throws IOException {
		HttpURLConnection connection =
			(HttpURLConnection) new URL(fileUrl).openConnection();
		connection.setRequestMethod(HEAD);
		connection.connect();

		boolean supportsMultipart = false;
		String acceptRanges = connection.getHeaderField(ACCEPT_RANGES);
		if (acceptRanges != null && acceptRanges.equals(BYTES)) {
			supportsMultipart = true;
		}

		connection.disconnect();
		return supportsMultipart;
	}

	public static boolean supportsResumableDownload(
		@NonNull String fileUrl) throws IOException {
		HttpURLConnection connection =
			(HttpURLConnection) new URL(fileUrl).openConnection();
		connection.setRequestMethod(HEAD);
		connection.connect();

		boolean supportsResume = false;
		String acceptRanges = connection.getHeaderField(ACCEPT_RANGES);
		String eTag = connection.getHeaderField(E_TAG);
		String lastModified = connection.getHeaderField(LAST_MODIFIED);
		if ((acceptRanges != null && acceptRanges.equals(BYTES)) ||
			eTag != null || lastModified != null) {
			supportsResume = true;
		}

		connection.disconnect();
		return supportsResume;
	}

	@NonNull
	public static String normalizeUrl(@NonNull String url) {
		if (!url.endsWith("/") && url.contains("."))
			return url.replaceAll("/$", "") + "/";
		return url;
	}

	@NonNull
	public static String extractDomainName(@NonNull String url) {
		try {
			URL parsedUrl = new URL(url);
			return parsedUrl.getHost();
		} catch (Throwable error) {
			logger.e("Error found while extracting domain name:", error);
			return "";
		}
	}

	@NonNull
	public static String appendPath(@NonNull String baseUrl,
	                                @NonNull String path) {
		if (!baseUrl.endsWith("/") && !path.startsWith("/")) baseUrl += "/";
		return baseUrl + path;
	}

	@NonNull
	public static String removeQueryParams(@NonNull String url) {
		try {
			URL parsedUrl = new URL(url);
			return parsedUrl.getProtocol() + "://" +
				parsedUrl.getHost() + parsedUrl.getPath();
		} catch (Throwable error) {
			logger.e("Error found while removing query name from url:", error);
			return "";
		}
	}

	@NonNull
	public static String addQueryParam(@NonNull String url, @NonNull String param,
	                                   @NonNull String value, boolean encode) {
		try {
			URL baseUrl = new URL(url);
			StringBuilder newUrl = new StringBuilder(baseUrl.toString());

			if (baseUrl.getQuery() == null) newUrl.append('?');
			else newUrl.append('&');

			newUrl.append(param);
			newUrl.append('=');

			if (encode) {
				newUrl.append(encode(value, UTF_8));
			} else newUrl.append(value);
			return newUrl.toString();
		} catch (Throwable error) {
			logger.e("Error found while adding query parameters to url:", error);
			return url;
		}
	}

	@NonNull
	public static List<String> generatePossibleURLs(@NonNull String baseUrl) {
		List<String> possibleURLs = new ArrayList<>();
		for (String domainEnd : URLDomains.getTopLevelDomains()) {
			possibleURLs.add(baseUrl + domainEnd);
		}
		return possibleURLs;
	}

	@Nullable
	public static String getOriginalURL(@NonNull String fileURL) {
		try {
			URLConnection urlConnection = new URL(fileURL).openConnection();
			HttpURLConnection connection = (HttpURLConnection) urlConnection;
			connection.setInstanceFollowRedirects(false);
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
				responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
				responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
				responseCode == HttpURLConnection.HTTP_CREATED) {
				return connection.getHeaderField("Location");
			}
			return null;
		} catch (Exception error) {
			logger.e("Error found while getting original url:", error);
			return null;
		}
	}

	@Nullable
	public static String fetchContentDispositionHeader(@NonNull String url) {
		HttpURLConnection connection = null;
		try {
			URL urlObj = new URL(url);
			connection = (HttpURLConnection) urlObj.openConnection();
			connection.setRequestMethod("GET");

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String contentDisposition =
					connection.getHeaderField(CONTENT_DISPOSITION);
				if (contentDisposition != null) return contentDisposition;
			}
		} catch (IOException error) {
			logger.e("Error found while fetching content disposition header:", error);
		} finally {
			if (connection != null) connection.disconnect();
		}
		return null;
	}

	@NonNull
	@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
	public static String encodeURL(@NonNull String url) {
		try {
			return encode(url, UTF_8);
		} catch (Exception error) {
			logger.e("Error found while encoding an url:", error);
			return "";
		}
	}

	@NonNull
	@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
	public static String decodeURL(@NonNull String url) {
		try {
			return URLDecoder.decode(url, UTF_8);
		} catch (Exception error) {
			logger.e("Error found while decoding an url:", error);
			return "";
		}
	}
}