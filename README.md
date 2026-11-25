# ConnLab

ConnLab is a desktop TCP test server built with Java 21 and JavaFX. You define in JSON configurations which incoming messages to expect and how to reply to them, start the server, and connect your TCP clients to it. It is designed for testing clients against a server that does not exist yet or is hard to reproduce.

## Features

- **JSON configurations in your own folder**: on first launch, ConnLab asks for a folder and creates a starter `default.json` configuration. You can open the folder in your system file explorer, switch to another folder, or reload configurations from disk at any time.
- **Visual configuration editor with draft validation**:
  - Create (`Ctrl+N`), duplicate, rename, filter (`Ctrl+F`), and delete (`Delete`) configurations.
  - Edits are made on a draft copy and validated as you type (unique name, port range `1–65535`, regular expression syntax, capture group count, and group index bounds).
  - Unsaved changes are marked with an `unsaved` badge in the sidebar, can be saved (`Ctrl+S`) or discarded, and prompt for confirmation before switching configurations or closing the window.
  - Message rules and reply building blocks can be added, reordered (move up/down), or deleted freely.
- **Automatic regex-based replies**:
  - Each rule matches incoming lines against a regular expression (`receivedRegex`) in order; the first matching rule builds the reply.
  - Replies are assembled from fixed text (`static`) and regex capture groups (`messagePart`, where group `0` is the whole message and `1..N` are the parenthesized groups).
  - Each rule can reply only to the sender or broadcast to every connected client (`broadcast`).
  - Auto-replies can be toggled globally per configuration (`hasToRespond`).
- **Try it (offline tester)**: test any sample incoming message directly inside the editor without starting the server. It previews the generated reply, shows which rule matched, indicates whether it would be sent to the sender or to every client, and warns if auto-respond is turned off.
- **Hot reload**: saving the configuration that is currently running applies the updated rules immediately without disconnecting clients (if the host or port was changed, the server keeps listening on the previous address until restarted).
- **Live server monitor**:
  - Top/bottom status indicators showing the active configuration (`running` badge), listening `host:port`, connected client count, and a live uptime counter.
  - **Connected clients table** with client ID, remote address, connection timestamp, and a **Kick** button to close individual client connections.
  - **Manual message sender** to send a custom message either to **All clients** or to a specific connected client (`#id address`).
  - **Per-configuration live log** with color-coded entries (`INFO`, `RECEIVED`, `SENT`, `ERROR`), auto-scroll (**Follow**), multi-line selection, **Copy** to clipboard (selected lines or the entire log), and **Clear**.
- **Light and dark themes**: powered by [AtlantaFX](https://github.com/mkpaz/atlantafx) (Primer Light / Primer Dark), switchable at any time from the top toolbar.

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+N` (`Cmd+N`) | Create a new configuration |
| `Ctrl+S` (`Cmd+S`) | Save changes to the current configuration |
| `Ctrl+F` (`Cmd+F`) | Focus the configuration search box |
| `F5` | Reload configurations from disk |
| `Delete` | Delete the selected configuration (when the list is focused) |

## Configuration format

Each configuration is stored as a `.json` file inside your configurations folder:

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
| `name` | Unique configuration name shown in the sidebar. |
| `description` | Optional description of the configuration. |
| `host` | IP/hostname to bind to. Leave blank or use `0.0.0.0` to listen on all interfaces. |
| `port` | TCP port (`1` to `65535`). |
| `hasToRespond` | When `false`, the server logs incoming messages without sending automatic replies. |
| `messages[].title` | Optional human-readable label for the rule. |
| `messages[].receivedRegex` | Java regular expression matched against each received line in order. The first match wins. |
| `messages[].broadcast` | `true` sends the automatic reply to all connected clients; `false` replies only to the sender. |
| `messages[].responseBuilder` | Ordered list of reply parts: `{"type": "static", "text": "..."}` for fixed text, or `{"type": "messagePart", "partIndex": N}` for regex capture group `N` (`0` is the full match). |

Messages are newline-delimited UTF-8 text: each line received is processed as one message, and each reply is sent as one line.

## Where the data is

| What | Location |
|---|---|
| Configurations | The folder chosen on first run (seeded with `default.json`). Can be changed from the UI. |
| Settings database | `~/.labtcp/db/labtcp.db` (SQLite) |
| Application logs | `~/.labtcp/logs/app.log` (rotated at 5 MB, last 5 files kept) |

Pass `-DremoveDb=true` when launching the JVM to reset the saved settings and prompt for the configurations folder again.

## Development

Requirements: **JDK 21**. Maven is not required; `./mvnw` (or `.\mvnw.cmd` on Windows) downloads it automatically.

```bash
./mvnw test        # run unit and integration tests
./mvnw package     # build the app jar and copy runtime dependencies to target/libs
java -jar target/libs/connlab-<version>.jar
```

### Building the Windows installer (`.msi`) locally

#### Option 1: On Windows (with `jpackage` + WiX 3)

`./mvnw install` runs `jpackage` to build `target/dist/ConnLab-<version>.msi`. On Windows, `jpackage` requires [WiX Toolset v3](https://github.com/wixtoolset/wix3/releases) (`candle.exe` and `light.exe`) on the `PATH`.

You can download the portable WiX 3.14 binaries and build the `.msi` from PowerShell:

```powershell
# One-time setup: download WiX 3.14 portable binaries and add them to your user PATH
Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip" -OutFile "$env:TEMP\wix314.zip"
Expand-Archive -Path "$env:TEMP\wix314.zip" -DestinationPath "$HOME\.wix314" -Force
$env:PATH += ";$HOME\.wix314"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";$HOME\.wix314", "User")

# Build target\dist\ConnLab-<version>.msi
.\mvnw.cmd clean install -DskipTests
```

If you only want to test the native `ConnLab.exe` bundle (`app-image`) on Windows **without installing WiX**:

```powershell
.\mvnw.cmd clean package -DskipTests
jpackage --name ConnLab --dest target\dist --type app-image --app-version 1.2.14 --input target\libs --vendor "Moises Guerola" --main-class io.github.gfmois.connlab.Launcher --main-jar connlab-1.2.14.jar --icon src\main\resources\favicon.ico --module-path target\libs --add-modules java.base,java.sql,java.desktop,java.prefs,javafx.controls,javafx.fxml,javafx.graphics
```

#### Option 2: On Linux or WSL (cross-building with the CI script)

[.github/release/build-msi.sh](.github/release/build-msi.sh) builds the Windows `.msi` entirely on Linux without a Windows machine:
1. Compiles the JAR with the Windows JavaFX artifacts (`-Djavafx.platform=win`).
2. Downloads the matching Linux and Windows Temurin 21 JDKs from Adoptium and uses `jlink` to create a stripped Windows runtime image.
3. Extracts `jpackageapplauncherw.exe` from the Windows JDK, embeds the ConnLab icon and version metadata using `resedit` ([.github/release/exe-resources.cjs](.github/release/exe-resources.cjs)), and writes `app/ConnLab.cfg`.
4. Packages the image into `target/dist/ConnLab-<version>.msi` using `wixl` and `wixl-heat` ([.github/release/installer.wxs](.github/release/installer.wxs)).

```bash
# Requires curl, jq, tar, unzip, wixl (msitools) and node/npm (Node 20+) on the PATH
.github/release/build-msi.sh
```

## CI, releases and self-hosted runner

| Workflow | Trigger | What it does | Runner |
|---|---|---|---|
| [`bump-patch.yml`](.github/workflows/bump-patch.yml) | Pull requests and pushes to `dev` | Runs unit/integration tests and a Qodana code quality scan. On pushes to `dev`, automatically bumps the Maven patch version (`x.y.z` → `x.y.z+1`) with `[skip ci]`. | Self-hosted Linux (`connlab`) |
| [`release.yml`](.github/workflows/release.yml) | Pushes to `main` | Checks whether tag `v<version>` already exists; if not, runs `.github/release/build-msi.sh` and publishes the GitHub release with `ConnLab-<version>.msi`. | Self-hosted Linux (`connlab`) |

Work goes into feature branches with a pull request to `dev`. A release is published by merging `dev` into `main`.

### Self-hosted Linux runner (`.github/runner`)

The repository includes a ready-to-run Docker setup in [`.github/runner/`](.github/runner/) (Ubuntu 24.04 with `msitools`/`wixl`, persistent `/home/runner` volume for the JDK, Maven, and Qodana caches, and resource limits for running alongside other services on a VPS):

```bash
cd .github/runner
read -rs RUNNER_TOKEN && export RUNNER_TOKEN   # registration token from GitHub (needed only the first time)
docker compose up -d --build
```

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE) (`GPL-3.0-or-later`).
Any modifications or derivative works distributed must be licensed under the same terms, and contributions are welcome via Pull Request.

