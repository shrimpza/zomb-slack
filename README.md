# ZOMB Slack

Slack Bot Client application for executing [ZOMB](https://github.com/shrimpza/zomb/)
requests.

## Building

The project is built via Ant, so simply execute `ant` from within the project
directory to build an executable `jar` package.

## Usage

```
$ java -jar zomb-slack.jar [options]
```

Options:

- `--slack-api-url=https://slack.com/api/`
  - The URL to the Slack API
- `--slack-token=xyz`
  - Your bot's authorization token
- `--zomb-url=http://your.zomb.host/`
  - ZOMB server URL
- `--zomb-key=zyx`
  - ZOMB client application key
- `--read-timeout=5000`
  - Read timeout for HTTP requests

## Interacting with the bot

Simply invite the bot to any channels you want it in, or begin a private
message session with the bot. Address the bot via the `@botname: query` format
and it will process and reply to those messages.

Any messages not addressed to the bot are skipped and not processed or sent to
the ZOMB server.