# AndyClaw

An open-source AI assistant for Android that can control your device, manage crypto wallets, run Linux commands, and operate autonomously in the background. Built by [Freedom Factory](https://github.com/EthereumPhone) for the [dGEN1](https://dgen.gg) and ethOS, but runs on any Android device.

## How It Works

AndyClaw is a single APK that operates in two modes depending on what device it's running on:

### On ethOS (dGEN1)

When installed on a device running ethOS, AndyClaw automatically detects the OS wallet service and unlocks **privileged mode**. No API key needed. authentication is handled by signing a message with your ethOS wallet.

**What you get in privileged mode on top of all other features:**

- Wallet integration (send transactions, check balances, manage tokens across Ethereum, Optimism, Polygon, Arbitrum, Base, and more)
- XMTP messaging (send and receive onchain messages)
- Full device control (WiFi, Bluetooth, mobile data, audio, power)
- Phone calls and call management
- Calendar read/write
- Full App management (install, uninstall, clear data, force stop)
- Device power controls (reboot, shutdown)
- Code execution
- Screen time and usage stats
- OS-managed heartbeat (the OS triggers the AI periodically, no foreground service needed)
- An autonomous sub-account wallet the AI controls for micro-payments and DeFi

### On Stock Android

When installed on a regular Android device, AndyClaw runs in **open mode**. You can use the integrated local model (Qwen2.5-1.5B) or bring your own API key (currently [OpenRouter](https://openrouter.ai) or [tinfoil.sh](https://tinfoil.sh/inference)).

**What you get in open mode:**

- Chat with the AI assistant
- Device info (battery, storage, device details)
- Clipboard read/write
- Contacts read/write
- Install apps via Aurora Store (no google play required)
- SMS send/read
- Camera capture
- Location and navigation
- App listing and launching
- Notification reading
- File system operations
- Shell commands
- Web search
- Long-term memory (the AI remembers things across sessions)
- Background heartbeat via foreground service
- Termux integration (if Termux is installed)
- ClawHub skills (install community-made skills)

**Setup:**

1. Install the APK
2. On first launch, select your mode (local Qwen2.5-1.5B model) or [OpenRouter](https://openrouter.ai) or [tinfoil.sh](https://tinfoil.sh/inference) api key
3. Tell the AI what you want help with
4. Name your AI (or keep the default)
5. Share your values/priorities
6. Choose which skills to enable (or turn on YOLO mode for full access)

## Features

### AI Agent Loop

AndyClaw uses an agentic tool-use loop. The AI can chain multiple tool calls together to accomplish complex tasks — checking your battery, looking up a contact, sending them a message, and logging what it did, all in a single conversation turn. Up to 20 tool iterations per request.

### Heartbeat

The heartbeat is the AI's autonomous background pulse. It wakes up periodically (default: 30 minutes, configurable) and can:

- Check device status (battery, connectivity, storage)
- Review notifications and messages
- Execute pending tasks
- Log what it found and did

On ethOS, the OS triggers the heartbeat directly. On stock Android, a foreground service keeps it alive.

Heartbeat logs are viewable in-app so you can see exactly what the AI did while you weren't looking.

### Skills System

Skills are modular capabilities the AI can use. There are 30+ built-in skills covering everything from device info or  installing apps to even crypto wallets. Skills are system-aware. Some are available on all Android devices, others require ethOS privileged access.

**ClawHub** lets you install and manage community-created skills written as SKILL.md files. These can be instruction-only (the AI follows written procedures) or Termux-executable (the AI runs scripts in a Linux environment).

### Shizuku Integration

[Shizuku](https://shizuku.rikka.app/) bridges the gap between a normal app and a system app on stock Android. When Shizuku is installed and activated, AndyClaw gains ADB-level permissions without requiring root, unlocking capabilities that were previously only available on ethOS:

- **App management** — silent install/uninstall, force stop, clear app data
- **System settings** — write to `Settings.Secure` and `Settings.Global` (e.g., change default DNS, toggle features)
- **Permission control** — grant/revoke runtime permissions for any app, including AndyClaw itself
- **Device control** — reboot, toggle WiFi/Bluetooth/mobile data, change screen brightness and timeout
- **Elevated shell** — run any command as the `shell` user (uid 2000), same as ADB. Access `dumpsys`, `logcat`, `am`, `pm`, `settings`, `input`, `wm`, `svc` and more
- **Input simulation** — simulate taps, swipes, and text input via `input` commands

**Setup:** Install the [Shizuku app](https://shizuku.rikka.app/), activate it via wireless debugging (Android 11+) or ADB, then enable the Shizuku skill in AndyClaw settings. The agent will automatically detect Shizuku availability and request permission when needed.

### Termux Integration

If [Termux](https://termux.dev) is installed, the AI gets a full Linux environment. It can run bash commands, install packages, execute scripts, and interact with the terminal. ClawHub skills can define Termux entrypoints that get synced and executed automatically.

See the **[Termux Setup Guide](guides/termux-setup.md)** for step-by-step installation and configuration instructions for stock android devices.

### Long-Term Memory

The AI has a semantic memory system backed by SQLite FTS4 and vector embeddings. It can store and retrieve memories across sessions. Be it facts you tell it, things it learns or context from conversations. Memories are automatically injected into the system prompt when relevant.

### Sessions

Chat history is persisted. You can resume previous conversations or start fresh ones.

### Extensions

Third-party apps can register as AndyClaw extensions, providing additional functions that get discovered and loaded automatically. Any Android app can become an extension by declaring metadata in its manifest and exposing an IPC bridge — a bound service, content provider, broadcast receiver, or activity intent. AndyClaw scans all installed packages, picks up extensions, and makes their functions available to the agent.

See the **[Extension Development Guide](ExtensionExample/README.md)** for a full walkthrough of the architecture, wire protocol, and step-by-step instructions for building your own extension. The `ExtensionExample/` module is a minimal working reference implementation.

## Requirements

- Android 15+ (API 35)
- (Optinal) AI api keys:
  - OpenRouter  (stock Android only — get one at [openrouter.ai](https://openrouter.ai))
  - Tinfoil  (stock Android only — get one at [tinfoil.sh](https://tinfoil.sh/inference))
- Optional: [Termux](https://termux.dev) for Linux command execution
- Optional: [Shizuku](https://shizuku.rikka.app/) for ADB-level device control without root

## Models

AndyClaw routes through either [OpenRouter](https://openrouter.ai), [tinfoil.sh](https://tinfoil.sh/inference)(stock android) or the integrated gateway (ethOS). The default model is `minimax/minimax-m2.5`. You can switch models in settings.

## Building From Source

### Prerequisites

- JDK 17
- Android SDK (API 36)
- [GitHub CLI](https://cli.github.com/) (`gh`) — for creating releases and managing secrets

### 1. Clone and configure

```bash
git clone https://github.com/EthereumPhone/AndyClaw.git
cd AndyClaw
```

Create `local.properties` in the project root with any keys you need for local development. This file is gitignored and never pushed.

```properties
# LLM API keys (optional — for local debug builds)
OPENROUTER_API_KEY=sk-or-...
TINFOIL_API_KEY=...
OPENAI_API_KEY=...
VENICE_API_KEY=...
CLAUDE_OAUTH_TOKEN=...
TELEGRAM_BOT_TOKEN=...
LLM_PROVIDER=openrouter
LLM_MODEL=minimax/minimax-m2.5

# Crypto/wallet keys (optional — only needed for wallet features)
BUNDLER_API=your_pimlico_bundler_key
ALCHEMY_API=your_alchemy_api_key
ZEROX_API_KEY=...
BANKR_API=...

# Premium LLM gateway override (optional)
PREMIUM_LLM_URL=https://your-gateway.com/api/llm

# Build as ethOS system app (optional)
SYSTEM_APP=true
```

### 2. Build locally

```bash
# Debug build (faster, includes logging, seeds API keys from local.properties)
./gradlew assembleDebug

# Release build (minified, requires signing config — see step 3)
./gradlew assembleRelease
```

Output APKs:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### 3. Set up release signing

Generate a keystore if you don't have one:

```bash
keytool -genkeypair \
  -keystore andyclaw-release.jks \
  -alias andyclaw \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_PASSWORD \
  -keypass YOUR_PASSWORD
```

Add signing config to your `local.properties`:

```properties
RELEASE_STORE_FILE=../andyclaw-release.jks
RELEASE_STORE_PASSWORD=YOUR_PASSWORD
RELEASE_KEY_ALIAS=andyclaw
RELEASE_KEY_PASSWORD=YOUR_PASSWORD
```

Keep your keystore file safe — if you lose it, you cannot push updates to the same app. The `.gitignore` already excludes `*.jks` and `*.keystore` files.

## Releases

Pre-built APKs are available on the [Releases](https://github.com/EthereumPhone/AndyClaw/releases) page. Download the latest APK and sideload it onto your device (you'll need to enable "Install from unknown sources").

### Creating a release from the CLI

```bash
# Build locally and create a release in one shot
./gradlew assembleRelease assembleDebug
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/debug/app-debug.apk \
  --title "AndyClaw v1.0.0" \
  --generate-notes

# For pre-release / testing builds
gh release create v1.0.0-beta.1 \
  app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/debug/app-debug.apk \
  --prerelease
```

### Automated releases via GitHub Actions

The `.github/workflows/release.yml` workflow builds both release and debug APKs in CI and publishes them as a GitHub Release. It can be triggered two ways:

**Option A: Push a version tag**

```bash
git tag v1.0.0
git push origin v1.0.0
```

Tags with a hyphen (e.g. `v1.0.0-beta.1`) are automatically marked as pre-releases.

**Option B: Manual trigger with auto-versioning**

The workflow can automatically bump the version based on the latest existing tag:

```bash
# Bump patch: v1.0.0 → v1.0.1
gh workflow run release.yml -f bump=patch

# Bump minor: v1.0.1 → v1.1.0
gh workflow run release.yml -f bump=minor

# Bump major: v1.1.0 → v2.0.0
gh workflow run release.yml -f bump=major

# Mark as pre-release
gh workflow run release.yml -f bump=patch -f prerelease=true
```

### Setting up GitHub Secrets

The release workflow needs signing credentials and (optionally) API keys. Set them via the CLI or the repo Settings > Secrets and variables > Actions page.

**Required (for APK signing):**

```bash
# Base64-encode your keystore and set it as a secret
base64 -i andyclaw-release.jks -o andyclaw-release.jks.b64
gh secret set KEYSTORE_BASE64 < andyclaw-release.jks.b64
rm andyclaw-release.jks.b64

# Set the remaining signing secrets
gh secret set RELEASE_STORE_PASSWORD --body "YOUR_PASSWORD"
gh secret set RELEASE_KEY_ALIAS --body "andyclaw"
gh secret set RELEASE_KEY_PASSWORD --body "YOUR_PASSWORD"
```

**Optional (API keys baked into the CI build):**

```bash
gh secret set OPENROUTER_API_KEY --body "sk-or-..."
gh secret set TINFOIL_API_KEY --body "..."
# Also available: OPENAI_API_KEY, VENICE_API_KEY, CLAUDE_OAUTH_TOKEN,
# TELEGRAM_BOT_TOKEN, LLM_PROVIDER, LLM_MODEL, BUNDLER_API,
# ALCHEMY_API, PREMIUM_LLM_URL, ZEROX_API_KEY, BANKR_API
```

Any secret you don't set resolves to an empty string, matching the default fallback in the build config. Only set the ones you need.

Verify your secrets are configured:

```bash
gh secret list
```

### API Keys: Local vs CI

API keys are managed separately for local development and CI builds — they never overlap:

| Environment | Where keys live | How they're loaded |
|---|---|---|
| Local development | `local.properties` (gitignored) | You edit the file directly |
| GitHub Actions | GitHub Secrets | Workflow writes `local.properties` from secrets at build time |

## Troubleshooting

### `Unresolved reference 'DEBUG_OPENROUTER_API_KEY'` (release build fails)

The `DEBUG_*` build config fields must be defined in both `debug` and `release` build types. If you see this error, check that `app/build.gradle.kts` has empty-string defaults for all `DEBUG_*` fields in the `release` block.

### `No default remote repository has been set` (gh CLI)

Set your default repo:

```bash
gh repo set-default OWNER/REPO
```

If you're working on a fork, use your fork's path (e.g. `your-username/AndyClaw`), not the upstream.

### `workflow release.yml not found on the default branch`

The workflow file must exist on your default branch (usually `main`) before `gh workflow run` can find it. If it only exists on a feature branch, push it to main first:

```bash
git checkout main
git checkout your-feature-branch -- .github/workflows/release.yml
git add .github/workflows/release.yml
git commit -m "Add release workflow"
git push origin main
git checkout your-feature-branch
```

### Release build succeeds but APK is unsigned

Make sure your `local.properties` has all four signing fields (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) and the keystore file exists at the specified path. For CI builds, verify the `KEYSTORE_BASE64` secret is set correctly — it should be the raw output of `base64 -i your-keystore.jks` with no extra whitespace.

### `assembleDebug` works but `assembleRelease` fails with compilation errors

Release builds enable R8 minification (`isMinifyEnabled = true`), which can surface issues that debug builds hide. Check `app/proguard-rules.pro` for missing keep rules if you see `ClassNotFoundException` or reflection-related errors at runtime.

## Permissions

AndyClaw requests a wide range of permissions to support its full skill set. On stock Android, most privileged permissions (device power, package management, system settings) are not usable without either ethOS or [Shizuku](https://shizuku.rikka.app/). With Shizuku activated, the agent can use ADB-level permissions to manage apps, write system settings, control device power, and more. Standard permissions (contacts, SMS, camera, location, etc.) are requested at runtime when a skill needs them.

## License

[GPL-3.0](LICENSE)