package net.osmand.plus.server;

import android.util.Log;
import android.webkit.MimeTypeMap;
import fi.iki.elonen.NanoHTTPD;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class ApiRouter {
	private static final String FOLDER_NAME = "server";
	private static final org.apache.commons.logging.Log LOG = PlatformUtil.getLog(ApiRouter.class);
	private final Map<String, ApiEndpoint> endpoints = new HashMap<>();
	private OsmandApplication application;

	public OsmandApplication getApplication() {
		return application;
	}

	public void setApplication(OsmandApplication application) {
		this.application = application;
		for (String s : endpoints.keySet()) {
			endpoints.get(s).setApplication(application);
		}
	}

	public NanoHTTPD.Response getStatic(String uri) {
		InputStream is;
		String mimeType = parseMimeType(uri);
		if (application != null) {
			try {
				is = application.getAssets().open(FOLDER_NAME + uri);
				if (is.available() == 0) {
					return ErrorResponses.response404;
				}
				return newFixedLengthResponse(
						NanoHTTPD.Response.Status.OK,
						mimeType,
						is,
						is.available());
			} catch (IOException e) {
				return ErrorResponses.response404;
			}
		}
		return ErrorResponses.response500;
	}

	public NanoHTTPD.Response route(NanoHTTPD.IHTTPSession session) {
		Log.d("SERVER", "URI: " + session.getUri());
		String uri = session.getUri();
		if (uri.equals("/")) {
			return getStatic("/go.html");
		}
		if (isApiUrl(uri)) {
			return routeApi(session);
		}
		return getStatic(uri);
	}

	public void register(String path, ApiEndpoint endpoint) {
		endpoint.setApplication(application);
		endpoints.put(path, endpoint);
	}

	private NanoHTTPD.Response routeApi(NanoHTTPD.IHTTPSession session) {
		String uri = session.getUri();
		int pathEnd = uri.indexOf("/", 1);
		if (pathEnd != -1) {
			uri = uri.substring(0, pathEnd);
		}
		ApiEndpoint endpoint = endpoints.get(uri);
		if (endpoint != null) {
			return endpoint.process(session);
		}
		return ErrorResponses.response404;
	}

	private boolean isApiUrl(String uri) {
		for (String endpoint : endpoints.keySet()) {
			int stringLength = endpoint.length();
			if (uri.startsWith(endpoint) &&
					(uri.length() == endpoint.length() || uri.charAt(stringLength) == '/')) {
				return true;
			}
		}
		return false;
	}


	private String parseMimeType(String url) {
		String type = "text/plain";
		if (url.endsWith(".js")) return "text/javascript";
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		}
		return type;
	}

	public static class ErrorResponses {
		public static NanoHTTPD.Response response404 =
				newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
						NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");

		public static NanoHTTPD.Response response500 =
				newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
						NanoHTTPD.MIME_PLAINTEXT, "500 Internal Server Error");
	}
}