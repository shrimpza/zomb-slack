package net.shrimpworks.zomb.slack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * zomb-slack main entry point.
 * <p>
 * Usage: <pre>jarfile [options]</pre>
 */
public class Main {

	private static final String OPTION_PATTERN = "--([a-zA-Z0-9-_]+)=(.+)";

	public static void main(String... args) throws IOException {
		// retrieve user's properties from file
		Path userProperties = Paths.get(String.format("%s%s%s",
													  System.getProperty("user.home"),
													  System.getProperty("file.separator"),
													  ".zomb-slack"));
		Properties properties = new Properties();

		if (Files.exists(userProperties)) {
			try {
				properties.load(Files.newInputStream(userProperties));
			} catch (IOException e) {
				System.err.printf("Could not load user properties: %s%n", e.getMessage());
				System.exit(2);
			}
		}

		// apply properties from the command line on top of user's properties
		properties = parseOptions(properties, args);

		if (properties.containsKey("config")) {
			Path customProperties = Paths.get(properties.getProperty("config"));
			if (!Files.exists(customProperties)) {
				System.err.printf("Config file does not exist: %s%n", properties.getProperty("config"));
				System.exit(3);
			} else {
				try {
					properties.load(Files.newInputStream(customProperties));
				} catch (IOException e) {
					System.err.printf("Could not load custom properties: %s%n", e.getMessage());
					System.exit(2);
				}
			}

		}

		ZombClient zombClient = new ZombClient(properties.getProperty("zomb-url"),
											   properties.getProperty("zomb-key"));

		SlackClient slackClient = new SlackClient(properties.getProperty("slack-api-url"),
												  properties.getProperty("slack-token"),
												  Integer.parseInt(properties.getProperty("slack-read-timeout", "5000")));

		slackClient.attach(new MessageCallback(zombClient));

		slackClient.connect();
	}

	private static Properties parseOptions(Properties properties, String... args) {
		Properties result = new Properties(properties);

		Pattern pattern = Pattern.compile(OPTION_PATTERN);

		for (String arg : args) {
			Matcher matcher = pattern.matcher(arg);
			if (matcher.matches()) result.setProperty(matcher.group(1), matcher.group(2));
		}

		return result;
	}

	private static class MessageCallback implements SlackClient.MessageCallback {

		private final ZombClient zomb;

		private MessageCallback(ZombClient zomb) {
			this.zomb = zomb;
		}

		@Override
		public void onMessage(SlackClient slack, SlackClient.Message message) {
			try {
				ZombClient.Response response = zomb.execute(message.user().name(), message.message());
				if (response.image() != null && !response.image().isEmpty()) {
					slack.send(new SlackClient.FancyMessage(message.channel(),
															Arrays.stream(response.response()).collect(Collectors.joining("\n")),
															response.image()));
				} else {
					slack.send(new SlackClient.OutboundMessage(message.channel(),
															   Arrays.stream(response.response()).collect(Collectors.joining("\n"))));
				}
			} catch (IOException e) {
				slack.send(new SlackClient.OutboundMessage(message.channel(), "Failed to process your request."));
			}
		}
	}
}
