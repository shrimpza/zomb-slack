package net.shrimpworks.zomb.slack;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import net.shrimpworks.zomb.slack.common.HttpClient;
import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class SlackClient implements Closeable {

	private static final Logger logger = Logger.getLogger(SlackClient.class.getName());

	private static final String START_URL = "%s/rtm.start?token=%s";

	private final String apiUrl;
	private final String token;

	private final HttpClient httpClient;

	private final Set<MessageCallback> listeners;

	private final Map<String, Channel> channels;
	private final Map<String, User> users;

	private final AtomicInteger idCounter;

	private SlackWebSocket webSocket;

	private String myId;
	private String myName;

	public SlackClient(String apiUrl, String token, int readTimeout) {
		this.apiUrl = apiUrl;
		this.token = token;

		this.listeners = new HashSet<>();

		this.channels = new HashMap<>();
		this.users = new HashMap<>();

		this.idCounter = new AtomicInteger(0);

		this.httpClient = new HttpClient(readTimeout);
	}

	public boolean connect() throws IOException {
		String authResponse = httpClient.get(String.format(START_URL, apiUrl, token));
		JsonObject json = Json.parse(authResponse).asObject();

		if (json.getBoolean("ok", false)) {
			myId = json.get("self").asObject().getString("id", "");
			myName = json.get("self").asObject().getString("name", "");

			// get current users
			json.get("users").asArray().forEach(v -> {
				JsonObject u = v.asObject();
				users.put(u.getString("id", null), new User(u.getString("id", null), u.getString("name", "")));
			});

			json.get("channels").asArray().forEach(v -> {
				JsonObject c = v.asObject();
				channels.put(c.getString("id", null), new Channel(c.getString("id", null), c.getString("name", "")));
			});

			json.get("ims").asArray().forEach(v -> {
				JsonObject c = v.asObject();
				if (users.get(c.getString("id", "")) != null) {
					channels.put(c.getString("id", null), new Channel(c.getString("id", null), users.get(c.getString("id", "")).name()));
				}
			});

			webSocket = new SlackWebSocket(URI.create(json.getString("url", null)));
			try {
				webSocket.connectBlocking();
			} catch (InterruptedException e) {
				webSocket.close();
				webSocket = null;
				throw new IOException("Failed to connect to Web Socket", e);
			}
		}

		return true;
	}

	@Override
	public void close() throws IOException {
		if (webSocket != null) webSocket.close();
	}

	public boolean attach(MessageCallback listener) {
		return listeners.add(listener);
	}

	public boolean detatch(MessageCallback listener) {
		return listeners.remove(listener);
	}

	public boolean send(OutboundMessage message) {
		webSocket.send(new JsonObject()
							   .add("id", idCounter.incrementAndGet())
							   .add("type", "message")
							   .add("channel", message.channel().id())
							   .add("text", message.text())
							   .toString());
		return true;
	}

	public boolean send(FancyMessage message) {
		try {
			httpClient.get(String.format("%s/chat.postMessage?token=%s&channel=%s&text=%s&icon_url=%s&username=%s", apiUrl, token,
										 message.channel().id(), message.text(), message.iconUrl(), myName));
		} catch (IOException e) {
			logger.warning("Failed to send fancy message, fallback to simple.");
			return send(new OutboundMessage(message.channel(), message.text()));
		}

		return true;
	}

	private class SlackWebSocket extends WebSocketClient {

		public SlackWebSocket(URI serverURI) throws IOException {
			super(serverURI);
			if (serverURI.toString().startsWith("wss")) {
				try {
					setWebSocketFactory(new DefaultSSLWebSocketClientFactory(SSLContext.getDefault()));
				} catch (NoSuchAlgorithmException e) {
					throw new IOException("Failed to get SSL context for secure web socket");
				}
			}
		}

		@Override
		public void onOpen(ServerHandshake serverHandshake) {
			logger.info(String.format("WebSocket connected as %s <%s>", SlackClient.this.myName, SlackClient.this.myId));
		}

		@Override
		public void onMessage(String s) {
			JsonObject m = Json.parse(s).asObject();
			switch (m.getString("type", "")) {
				case "message":
					Channel channel = SlackClient.this.channels.get(m.getString("channel", ""));
					// if a new direct message, and channel is unknown, create one on demand.
					if (channel == null && m.getString("channel", "").startsWith("D")) {
						channel = new Channel(m.getString("channel", null), users.get(m.getString("user", null)).toString());
						SlackClient.this.channels.put(m.getString("channel", null), channel);
					}

					if (channel != null) {
						if (channel.id().startsWith("D") || m.getString("text", "").matches(String.format("<@%s>: .+", myId))) {
							Message msg = new Message(channel, users.get(m.getString("user", "")),
													  m.getString("text", "").replaceFirst(String.format("<@%s>: ", myId), "")
													   .replaceAll("<(https?://.+)>", "$1"));
							SlackClient.this.listeners.parallelStream().forEach(l -> l.onMessage(SlackClient.this, msg));
						}
					} else {
						logger.warning("Unknown channel " + m.getString("channel", ""));
					}

					break;
				case "channel_created":
					JsonObject c = m.get("channel").asObject();
					SlackClient.this.channels.put(c.getString("id", null), new Channel(c.getString("id", null), c.getString("name", "")));
					break;
				default:
					break;
			}
		}

		@Override
		public void onClose(int i, String s, boolean b) {
			logger.info("WebSocket closed");
		}

		@Override
		public void onError(Exception e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public static interface MessageCallback {

		void onMessage(SlackClient slack, Message message);
	}

	public static class Message {

		private final Channel channel;
		private final User user;
		private final String message;

		public Message(Channel channel, User user, String message) {
			this.channel = channel;
			this.user = user;
			this.message = message;
		}

		public Channel channel() {
			return channel;
		}

		public User user() {
			return user;
		}

		public String message() {
			return message;
		}
	}

	public static class Channel {

		private final String id;
		private final String name;

		public Channel(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public String id() {
			return id;
		}

		public String name() {
			return name;
		}
	}

	public static class User {

		private final String id;
		private final String name;

		public User(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public String id() {
			return id;
		}

		public String name() {
			return name;
		}
	}

	public static class OutboundMessage {

		private final Channel channel;
		private final String text;

		public OutboundMessage(Channel channel, String text) {
			this.channel = channel;
			this.text = text;
		}

		public Channel channel() {
			return channel;
		}

		public String text() {
			return text;
		}
	}

	public static class FancyMessage extends OutboundMessage {

		private final String iconUrl;

		public FancyMessage(Channel channel, String text, String iconUrl) {
			super(channel, text);
			this.iconUrl = iconUrl;
		}

		public String iconUrl() {
			return iconUrl;
		}
	}
}
