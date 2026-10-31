# ChatSummon - Chat-Activated Creature Summoning Mod

[![Fabric 1.21.10](https://img.shields.io/badge/Fabric-1.21.10-white?labelColor=informational)](https://fabricmc.net/)
[![Kotlin 2.4.10](https://img.shields.io/badge/Kotlin-2.4.10-7f52ff?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java 21](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/Wmsqaq/chatsummon-mod?color=success)](LICENSE)
[![Latest Commit](https://img.shields.io/github/last-commit/Wmsqaq/chatsummon-mod/main?logo=github)](https://github.com/Wmsqaq/chatsummon-mod/commits/main)

A multiplayer-focused mod for Fabric 1.21.10: The client continuously monitors the microphone, streams audio to the cloud for speech recognition (ASR), matches the recognized text to a creature name, sends a packet to the server, and the server spawns that creature at the location of **another player**.

## Features

| Feature | Description |
|---|---|
| Continuous Microphone Monitoring | Automatically begins monitoring upon entering the game; no manual activation required. |
| Dual ASR Provider Support | Supports streaming ASR via Volcengine (ByteDance) or Alibaba Cloud; switch between them with a single click. |
| Intelligent Matching Algorithm | Combines exact matching, alias matching, and cloud-based "hotword" list lookups. |
| Cross-Player Spawning | Once a creature name is identified, it is spawned at the location of **another online player**—not at the speaker's own location. |
| GUI Configuration Panel | Built on Cloth Config; all parameters can be adjusted in-game without needing to restart. |
| HUD Status Bar | Displays real-time audio waveforms, recognized text, cooldown progress, and monitoring status. |
| Robust Error Handling | Includes automatic WebSocket reconnection, ASR session restarts after 10 seconds of inactivity, and audio queue cleanup. |
| Credential Masking | Sensitive credentials are masked in the GUI and are never written to log files. |
| Hotkey | Press **V** to pause or resume microphone monitoring. |
| Vanilla-Friendly | Does not add new entities or items, and does not alter the behavior of existing vanilla creatures. |



## 前置要求

| 名称 | 最低版本 | 说明 |
|---|---|---|
| Minecraft | 1.21.10 | Yarn mappings `1.21.10+build.3` |
| Fabric Loader | `>= 0.19.3` | - |
| Fabric API | `>= 0.138.4+1.21.10` | 包含网络、命令、HUD 等钩子 |
| Fabric Language Kotlin | `>= 1.13.13+kotlin.2.4.10` | Kotlin 语言适配器 |
| Cloth Config | `>= 20.0.149` | GUI 设置面板依赖 |
| JDK | **21** | 构建与运行强制要求，JDK 8/17 不可用 |

## Installation

1. Place `chatsummon-mod-Public-1.jar` into your `mods/` directory.
2. Launch the game to generate the default configuration file at `config/chatsummon.json`.
3. In-game, enter `/chatsummon settings` to open the GUI configuration panel.
4. Enter your cloud service provider credentials and save; changes take effect immediately without requiring a restart.

## Configuration

### Cloud Service Provider Credentials (Required)

The mod supports two service providers, which can be toggled in the settings panel under "Speech Recognition → Provider":

**Volcengine (ByteDance – Recommended)**

| Field | Setting Location | Default Value |
|---|---|---|
| API Key (`X-Api-Key`) | `volcengineApiKey` | Empty (enter your own) |
| Resource ID | `volcengineResourceId` | `volc.seedasr.sauc.duration` |
| Cluster / Instance ID | `volcengineCluster` | Empty (enter after binding an instance in the console) |
| Cloud Hotword List ID | `boostingTableId` | Empty (highly recommended to create a hotword list for in-game entities/mobs) |

Models and Billing:

| Model | Billing Method | Resource ID |
|---|---|---|
| Doubao Streaming ASR 2.0 (Recommended) | Hourly-based | `volc.seedasr.sauc.duration` |
| Doubao Streaming ASR 2.0 | Concurrency-based | `volc.seedasr.sauc.concurrent` |
| Doubao Streaming ASR 1.0 | Hourly-based | `volc.bigasr.sauc.duration` |

**Alibaba Cloud (Aliyun)**

| Field | Description |
|---|---|
| `aliyunAppKey` | Speech Recognition App Key |
| `aliyunAccessKeyId` | AccessKey ID |
| `aliyunAccessKeySecret` | AccessKey Secret |
| `boostingTableId` | `vocabulary_id` (custom keyword list) |

### Summoning Rules (Common)

| Configuration | Default | Description |
|---|---|---|
| `summonRange` | ∞ | Maximum distance (in blocks) between the target player and the speaker |
| `cooldownSeconds` | `10` | Cooldown period between summons (in seconds) |
| `maxMobsPerSummon` | `5` | Maximum number of entities to spawn per successful match |
| `summonAtSpeakerIfNoTarget` | `true` | Whether to spawn entities at the speaker's location if no other player is found within range |
| `matchDelaySeconds` | `1.0` | Delay (in seconds) after a pause in speech before triggering a match (debounce) |
| `recognitionThreshold` | `0.6` | ASR confidence threshold; matches below this value are ignored |

### Miscellaneous

| Configuration | Default | Description |
|---|---|---|
| `alwaysListening` | `true` | Whether to automatically start listening upon entering the game |
| `microphoneName` | Empty (System Default) | Specifies the microphone to use |
| `enableNoiseReduction` | `true` | Enables client-side FFT-based software noise reduction |
| `noiseReductionStrength` | `2` | Noise reduction intensity (0–3) |
| `hudPosition` | `TOP_LEFT` | HUD position: `TOP_LEFT` / `TOP_RIGHT` / `BOTTOM_LEFT` / `BOTTOM_RIGHT` |
| `debugMode` | `false` | Outputs detailed logs (including raw ASR text) |
| `logToFile` | `true` | Writes logs to `logs/chatsummon/chatsummon-YYYY-MM-DD.log` |

> Note: If the `schemaVersion` is lower than the current version (6), the existing configuration file will be **completely overwritten** with default values. Please back up `config/chatsummon.json` before upgrading. ## How to Use

1. Ensure that **"● Listening"** (a solid green circle) is displayed in the top-left corner of the HUD.
2. Speak the name of a creature directly into your microphone:
```
"Zombie"   ​​    → Spawns a zombie at another player's location
"Enderman Creeper" → Spawns a hybrid creature
```
3. A countdown appears on the HUD while the feature is on cooldown; when a command is recognized, it will be displayed instantly via a semi-transparent banner.

### Commands

| Command | Description |
|---|---|
| `/chatsummon on` | Enable monitoring |
| `/chatsummon off` | Disable monitoring |
| `/chatsummon status` | Check status |
| `/chatsummon reload` | Reload configuration (hot-reload) |
| `/chatsummon settings` | Open the GUI settings panel |

### Alias ​​Support

The mod includes built-in mappings for over 80 entities, along with an alias table. Examples:

| Type | Examples |
|---|---|
| Aliases | "Little White" (小白) → Skeleton / "Little Black" (小黑) → Enderman / "Pigman" → Zombified Piglin |
| Exact Full Names | Warden (supports various Chinese translations/synonyms) → Warden |

## For Developers: Building the Project

### Prerequisites

- JDK **21** (The path is hardcoded in `gradle.properties` under `org.gradle.java.home`; please update it to your local path before the first use.)
- No separate Gradle installation required — use `./gradlew.bat` (on Windows) in the project root to automatically download Gradle 9.5.1.
- The Gradle Wrapper is configured to use a **Tencent Cloud mirror**; please be patient while dependencies are downloaded during the initial build.

### Build Commands

```powershell
# Windows PowerShell
cd d:\idea\chatsummon-mod

# Full build (includes tests → generates remapped JAR)
.\gradlew.bat build

# Output location: build\libs\chatsummon-mod-Public-1.jar

# Run unit tests only
.\gradlew.bat test
```

### Project Structure

```
src/main/kotlin/com/nitelyfe/chatsummon/
├── ChatSummonMod.kt          # Server-side entry point: registers packets, commands, and events
├── ChatSummonClient.kt       # Client-side entry point: audio capture and matching dispatcher
├── audio/                     # 16kHz microphone capture + FFT-based software noise reduction
├── recognition/               # WebSocket-based streaming recognizers (Volcengine/Alibaba Cloud)
├── mob/                       # Entity mapping, aliases, and matching logic
├── summon/                    # Server-side summoning logic + configuration hot-reloading (via mtime)
├── network/                   # Client-to-server (C2S) summoning packets + client logging packets
├── config/                    # Cloth Config data classes + schema migration
├── gui/                       # Settings interface + masked credential input fields
├── keybind/                   # Key listener (default: 'V') for toggling
├── hud/                       # Rendering for volume bars, status indicators, and banners
├── command/                   # Subcommands for '/chatsummon'
└── util/                      # Logging proxies + text segmentation utilities
```
```

### Build Notes

1. Use `net.fabricmc.fabric-loom-remap` 1.17-SNAPSHOT (a non-standard version of `fabric-loom`); keep the `loom {}` block empty.
2. The code package name is `com.nitelyfe.chatsummon`, while `maven_group` in `gradle.properties` is set to `com.mango.chatsummon` (this is for publishing purposes only; do not change the package name).
3. The only additional dependencies are **Gson 2.11.0** and **Cloth Config**. WebSocket functionality is handled via the built-in `java.net.http.WebSocket` (JDK); no external networking libraries are used.

## FAQ

**Q: The HUD shows "API not configured" after startup?**
A: Open `/chatsummon settings`, navigate to the "Speech Recognition" category, enter your Volcengine (火山引擎) API Key and Cluster ID, then save.

**Q: Recognition is inaccurate or there are too many false triggers?**
A:
1. It is recommended to create a "hotword list" (custom vocabulary) for creature names via the Volcengine **Self-Learning Platform** and enter the `boostingTableId` into the settings (this offers the best results at the lowest cost).
2. Try increasing the `recognitionThreshold` (to a value between 0.75 and 0.85).
3. Enable noise reduction and adjust its intensity, or use the mod in a quieter environment.

**Q: The HUD shows "Connecting..." for more than 20 seconds without any recognition results?**
A: There is an inherent initialization delay for the ASR (Automatic Speech Recognition) server. It is normal for the system to return no text during the first 20 seconds after connecting; this is not a bug. It will resume automatically after this period.

**Q: How do I pause listening?**
A: Press the **V** key, or use the command `/chatsummon off`.

**Q: Does the server need this mod installed?**
A: **Yes.** The client handles speech recognition, while the server receives the C2S (client-to-server) packets and executes the summons. However, the server **does not** require any ASR credentials and will not access the microphone.

## License


