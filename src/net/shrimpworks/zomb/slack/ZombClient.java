package net.shrimpworks.zomb.slack;

import java.io.IOException;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import net.shrimpworks.zomb.slack.common.HttpClient;

public class ZombClient {

	private final String url;
	private final String key;

	private final HttpClient client;

	public ZombClient(String url, String key) {
		this.url = url;
		this.key = key;

		this.client = new HttpClient(5000); // TODO configurable
	}

	public Response execute(String user, String query) throws IOException {
		JsonObject request = new JsonObject()
				.add("key", key)
				.add("user", user)
				.add("query", query);

		JsonObject json = Json.parse(client.post(url, request.toString())).asObject();

		String[] responseLines = new String[json.get("response").asArray().size()];
		for (int i = 0; i < json.get("response").asArray().size(); i++) {
			responseLines[i] = json.get("response").asArray().get(i).asString();
		}

		return new Response(
				json.get("plugin").asString(),
				json.get("user").asString(),
				json.get("query").asString(),
				responseLines,
				json.get("image").asString()
		);
	}

	public static final class Response {

		private final String plugin;
		private final String user;
		private final String query;
		private final String[] response;
		private final String image;

		public Response(String plugin, String user, String query, String[] response, String image) {
			this.plugin = plugin;
			this.user = user;
			this.query = query;
			this.response = response;
			this.image = image;
		}

		public String plugin() {
			return plugin;
		}

		public String user() {
			return user;
		}

		public String query() {
			return query;
		}

		public String[] response() {
			return response.clone();
		}

		public String image() {
			return image;
		}
	}
}
