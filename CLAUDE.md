# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AndyClaw is an open-source Android AI assistant for the dGEN1 device (ethOS) and stock Android. It's an agentic tool-use system that can control devices, manage crypto wallets, run Linux commands, and operate autonomously in the background.

- **Language**: Kotlin 2.0+ with Jetpack Compose
- **Target**: Android 15+ (API 35), Android 16 (API 36) SDK
- **Architecture**: MVVM + Agent-based tool loop

## Build Commands

```bash
# Build release APK
./gradlew assembleRelease

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Build specific module
./gradlew :app:assembleRelease
./gradlew :AndyClaw:build
```

Output APK location: `app/build/outputs/apk/release/`

## Project Structure

```
/app                    # Main application module
  /src/main/java/.../
    /agent/            # AgentLoop, AgentRunner - core agentic execution
    /ui/               # Compose screens (chat/, settings/, clawhub/, etc.)
    /skills/builtin/   # 40+ built-in skills (wallet, termux, device control)
    /skills/external/  # Extension skill discovery
    /llm/              # LLM clients (Anthropic, OpenRouter, Tinfoil, Local)
    /services/         # Android services (Heartbeat, Accessibility, etc.)

/AndyClaw              # Shared library module
  /src/main/java/.../
    /agent/            # AgentRunner interface, AgentResponse
    /extensions/       # Extension engine, APK scanner, IPC bridges
    /memory/           # Semantic memory (vector embeddings, Room DB)
    /sessions/         # Chat session persistence
    /skills/           # Skill base classes and interfaces
  /schemas/            # Room database migration schemas

/ExtensionExample      # Reference extension implementation
/tinfoil-bridge/       # Pre-built AAR for Tinfoil inference
```

## Architecture

### Agent Loop
The core pattern is an agentic tool-use loop in `AgentLoop.kt`:
1. User prompt + system prompt + available skills sent to LLM
2. LLM streams response, may include tool calls
3. Tools executed sequentially, results fed back to LLM
4. Up to 20 iterations per request
5. ApprovalDialog gates sensitive operations (wallet sends, app installs)

### Skill System
All skills implement `AndyClawSkill` interface with manifest-driven discovery:
```kotlin
interface AndyClawSkill {
    val id: String
    val name: String
    val baseManifest: SkillManifest      // Available on all Android
    val privilegedManifest: SkillManifest?  // Requires ethOS
    suspend fun execute(tool: String, params: JsonObject, tier: Tier): SkillResult
}
```
Tiers: `TIER_ALL`, `TIER_ETHOSONLY`, `TIER_PRIVILEGED`

### LLM Clients
Pluggable clients in `/llm/`:
- `AnthropicClient` - Claude via OpenRouter
- `ClaudeOauthClient` - Claude OAuth flow
- `LocalLlmClient` - Qwen2.5-1.5B via Llamatik
- `TinfoilClient` / `TinfoilProxyClient` - Tinfoil.sh inference

### Memory System
Semantic memory backed by Room + vector embeddings:
- `MemoryManager.kt` - Store/retrieve memories
- `MemoryRepository.kt` - Chunking and search
- SQLite FTS4 for full-text search
- Cosine similarity for vector matching
- Auto-injected into system prompt when relevant

### Heartbeat System
Autonomous background execution:
- ethOS triggers directly; stock Android uses foreground service
- `HeartbeatAgentRunner.kt` executes periodic agent loops
- Results stored in `HeartbeatLogStore`

### Extension System
Third-party apps register as extensions via manifest metadata:
- Discovery: `ApkExtensionScanner.kt`
- IPC bridges: bound service (AIDL), content provider, broadcast, intent
- Wire protocol: JSON with function signatures

## Configuration

Create `local.properties` for optional features:
```properties
BUNDLER_API=<pimlico-key>        # Wallet bundler
ALCHEMY_API=<alchemy-key>        # RPC provider
PREMIUM_LLM_URL=https://...      # Override LLM gateway
ZEROX_API_KEY=<key>              # 0x swap integration
BANKR_API=<key>                  # Portfolio tracking
SYSTEM_APP=true                  # Build as ethOS system app
```

## Key Dependencies

- Compose BOM 2024.09 for UI
- Room 2.7.1 for persistence
- OkHttp 4.12.0 with SSE for streaming
- Kotlinx-serialization 1.7.3 for JSON
- KSP 2.0.21 for annotation processing
- ethOS SDKs: DgenSubAccountSDK, MessengerSDK, ContactsSDK, TerminalSDK
- Kethereum 0.85.7 for ENS resolution

## Testing

Unit tests: `app/src/test/java/`
Instrumented tests: `app/src/androidTest/java/`

Run a single test class:
```bash
./gradlew test --tests "org.ethereumphone.andyclaw.ExampleUnitTest"
```

## AIDL Interfaces

Located in `app/src/main/aidl/org/ethereumphone/andyclaw/`:
- `IAndyClawSkill.aidl` - Extension skill interface
- `ipc/IHeartbeatService.aidl` - Heartbeat binding
- `ipc/ILauncherService.aidl` - Launcher integration

## Two Wallet Architecture

The app manages two wallets:
1. **User wallet** - ethOS system wallet, requires on-device approval for transactions
2. **Agent wallet** - Sub-account controlled autonomously by the AI

Use `send_token`/`send_native_token` for transactions (handles decimals). Avoid raw `propose_transaction` unless doing advanced contract calls.
