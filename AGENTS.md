# AGENTS.md

Fabric 1.21.10 模组（Kotlin），语音召唤生物：客户端持续监听麦克风 → 火山引擎语音识别 → 匹配生物名 → C2S 数据包 → 服务端在其他玩家处生成生物。

## 技术栈与入口
- Fabric MC `1.21.10` + Yarn mappings + Kotlin `2.4.10` + Java 21
- 入口在 `src/main/resources/fabric.mod.json`：服务端 `ChatSummonMod`（main）、客户端 `ChatSummonClient`（client），均用 `"adapter": "kotlin"`（依赖 fabric-language-kotlin）
- 代码全在 `src/main/kotlin/com/nitelyfe/chatsummon/`，模块：`config` / `audio` / `recognition` / `mob` / `summon` / `network` / `keybind` / `hud` / `util`

## 构建与验证（Windows）
- `.\gradlew.bat build` — 全量构建（含测试）
- `.\gradlew.bat test` — 仅测试（JUnit Jupiter + kotlin-test）
- 无 lint/format/CI 配置
- `gradle.properties` 硬编码本机 JDK 路径 `org.gradle.java.home=D:/jdk21/jdk-21.0.12+8`；gradle wrapper 走腾讯镜像（`mirrors.cloud.tencent.com`），首次构建慢

## 关键文档
- `src/main/resources/assets/chatsummon/README.md` — 中文用户文档（功能、配置项、API、FAQ）
- `.trae/documents/voice-summon-mod-plan.md` — 原始开发计划，含"初版 vs 修订版"架构差异与设计决策

## 易踩的坑
1. **包名与 maven_group 不一致**：`gradle.properties` 里是 `com.mango.chatsummon`，代码实际包名是 `com.nitelyfe.chatsummon`（已迁移）。entrypoint 引用 nitelyfe 包，勿改回。
2. **构建插件是 `net.fabricmc.fabric-loom-remap`（1.17-SNAPSHOT）**，不是标准 fabric-loom；`loom {}` 块为空，别加未验证配置。
3. **配置 schema 版本重置**：`ModConfig.CURRENT_VERSION = 2`。`config/chatsummon.json` 的 `schemaVersion` 低于当前值时，首次启动会用默认值**整体覆盖（含用户自定义项）**。改配置结构时自增版本号并知晓此行为。
4. **默认配置内嵌真实火山引擎凭据**（apiKey/appId/accessKey）：是现存事实，勿新增泄露、勿在日志打印。
5. **服务端配置热加载**：`SummonManager` 按 `config/chatsummon.json` 的 mtime 变更重新加载缓存，勿在每次召唤时读盘。
6. **客户端匹配是增量累积 + 防抖**：`ChatSummonClient` 用 `matchLock`（ReentrantLock）串行化"识别回调线程"与"匹配调度线程"，识别文本停顿 `matchDelaySeconds`（默认 1s）后只对新增内容匹配，避免历史词重复触发召唤。改动识别/匹配流程时必须保持该线程安全模型。
7. **`ChatSummonMod.broadcastJoinNotice` 是写死的功能**：玩家进服广播版权/版本声明（含"特供版本"、按玩家名 "ThisWuming" 走特殊分支），勿误删。
8. **根目录 `latest.log`、`text.txt` 是运行时产物**（非源码；模组日志在 `logs/chatsummon/`）。

## 网络与识别
- 自定义包：`ChatSummonPacket`（playC2S，ID `chatsummon:summon`），在 `ChatSummonMod` 注册、`SummonManager` 处理
- 语音识别用 JDK 内置 `java.net.http.WebSocket`，无额外 WS 依赖；额外依赖仅 Gson + Cloth Config（均 `modImplementation`）
- 服务端不做任何语音处理，只需接收召唤指令
