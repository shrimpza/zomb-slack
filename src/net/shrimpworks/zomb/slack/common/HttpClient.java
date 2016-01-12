package net.shrimpworks.zomb.slack.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A simple HTTP client utility, capable of performing basic HTTP requests.
 */
public class HttpClient {

	private final int readTimeout;

	public HttpClient(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	/**
	 * GET data from a URL
	 *
	 * @param requestUrl url to GET
	 * @return http server response
	 * @throws java.io.IOException something went wrong
	 */
	public String get(String requestUrl) throws IOException {
		return httpRequest("GET", requestUrl, null);
	}

	/**
	 * POST data to a URL
	 *
	 * @param requestUrl the URL of the page/service to submit data to
	 * @param body       HTTP encoded name=value pairs, separated with "&amp;", or POST body
	 * @return http server response
	 * @throws java.io.IOException something went wrong
	 */
	public String post(String requestUrl, String body) throws IOException {
		return httpRequest("POST", requestUrl, body);
	}

	/**
	 * DELETE a URL
	 *
	 * @param requestUrl the URL of the page/service to delete
	 * @return http server response
	 * @throws java.io.IOException something went wrong
	 */
	public String delete(String requestUrl) throws IOException {
		return httpRequest("DELETE", requestUrl, null);
	}

	private String httpRequest(String method, String requestUrl, String body) throws IOException {
		HttpURLConnection conn = null;

		try {
			URL url = new URL(requestUrl);
			conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod(method);
			conn.setDoOutput(body != null && !body.isEmpty());
			conn.setReadTimeout(readTimeout);
			conn.connect();

			if (body != null && !body.isEmpty()) {
				try (OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream())) {
					wr.write(body);
					wr.flush();
				}
			}

			try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				String line;
				StringBuilder sb = new StringBuilder();
				while ((line = rd.readLine()) != null) {
					sb.append(line);
				}

				return sb.toString();
			}
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

}
