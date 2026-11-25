# ConnLab

ConnLab is a desktop TCP test server. You describe in a JSON file which messages to expect and how to answer
them, start the server and connect your client to it. It is meant for testing clients against a server that
does not exist yet, or that is hard to reproduce.

## Features

- **Configurations as JSON files** in a folder you choose, with an editor to create, duplicate, change and
  delete them. The editor validates as you type and saves with `Ctrl+S`.
- **Automatic replies**: each message has a regular expression, and its reply is built from fixed text and
  groups captured by that regex. A reply can go to the sender only or to every client.
- **Try it**: test a message against the configuration from the editor, without starting the server.
- **Monitor**: connected clients (you can disconnect any of them), a live log of what was received and sent,
  and a box to send a message to one client or to all of them.
- **Hot reload**: saving the configuration that is running updates the replies without disconnecting the
  clients.
- Light and dark themes ([AtlantaFX](https://github.com/mkpaz/atlantafx)).

## Configuration format

```json
{
    "name": "Default",
    "description": "Default file for a simple configuration",
    "host": "127.0.0.1",
    "port": 6969,
    "hasToRespond": true,
    "messages": [
        {
            "title": "Hello <name> Response",
            "broadcast": true,
            "receivedRegex": "^Hello, (.*)$",
            "responseBuilder": [
                { "type": "static", "text": "Bye, " },
                { "type": "messagePart", "partIndex": 1 },
                { "type": "static", "text": "!" }
            ]
        }
    ]
}
```

| Field | Meaning |
|---|---|
| `name` | Unique name, shown in the list. |
| `host` | Address to listen on. Leave it out (or use `0.0.0.0`) to listen on every interface. |
| `port` | TCP port, 1 to 65535. |
| `hasToRespond` | `false` only logs what arrives, without answering. |
| `messages[].receivedRegex` | Each received line is matched against the messages in order. The first match answers. |
| `messages[].broadcast` | `true` sends the reply to every connected client, not only the sender. |
| `responseBuilder` | Parts of the reply: `static` adds `text`, `messagePart` adds the regex group `partIndex`. |

Messages are lines of UTF-8 text: every line received is one message, and every reply is sent as one line.

## Where the data is

| What | Where |
|---|---|
| Configurations | The folder chosen on the first run. It can be changed from the app. |
| Settings database | `~/.labtcp/db/labtcp.db` |
| Logs | `~/.labtcp/logs/app.log` (rotated at 5 MB, the last 5 are kept) |

Run with `-DremoveDb=true` to forget the settings and choose the configurations folder again.

## Development

Requirements: JDK 21. Maven is not needed, the wrapper downloads it.

```bash
./mvnw test        # unit and integration tests
./mvnw package     # app jar and its dependencies in target/libs
java -jar target/libs/connlab-<version>.jar
```

### Building the Windows installer (`.msi`) locally

**On Windows** (using `jpackage`):

`./mvnw install` builds `target/dist/ConnLab-<version>.msi` with `jpackage`, which requires [WiX Toolset v3](https://github.com/wixtoolset/wix3/releases) (`candle.exe` and `light.exe`) on the `PATH`. You can download the portable binaries and build from PowerShell with:

```powershell
# One-time setup: download WiX 3.14 portable binaries and add them to your user PATH
Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip" -OutFile "$env:TEMP\wix314.zip"
Expand-Archive -Path "$env:TEMP\wix314.zip" -DestinationPath "$HOME\.wix314" -Force
$env:PATH += ";$HOME\.wix314"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";$HOME\.wix314", "User")

# Build the MSI in target\dist\ConnLab-<version>.msi
.\mvnw.cmd clean install -DskipTests
```

**On Linux / WSL** (using the CI script):

```bash
# Needs curl, jq, tar, unzip, wixl (msitools) and node/npm on the PATH
.github/release/build-msi.sh
```

## CI and releases

| Workflow | When | Runner |
|---|---|---|
| `bump-patch.yml` | Pull requests and pushes to `dev`: tests and Qodana. On `dev` it then bumps the patch version. | Self-hosted Linux (`connlab`) |
| `release.yml` | Pushes to `main`: builds the MSI and publishes the release `v<version>` with it, unless it exists. | Self-hosted Linux (`connlab`) |

Work goes into feature branches with a pull request to `dev`. A release is made by merging `dev` into `main`.
