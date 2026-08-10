# VoiceSummon - 语音召唤生物模组

[![Fabric 1.21.10](https://img.shields.io/badge/Fabric-1.21.10-white?labelColor=informational)](https://fabricmc.net/)
[![Kotlin 2.4.10](https://img.shields.io/badge/Kotlin-2.4.10-7f52ff?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java 21](https://img.shields.io/badge/Java-21-E76F00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/Wmsqaq/voicesummon-mod?color=success)](LICENSE)
[![Latest Commit](https://img.shields.io/github/last-commit/Wmsqaq/voicesummon-mod/main?logo=github)](https://github.com/Wmsqaq/voicesummon-mod/commits/main)

Fabric 1.21.10 多人协作玩法模组：客户端持续监听麦克风 → 云端流式语音识别 → 匹配生物名 → C2S 数据包 → 服务端在**其他玩家**位置生成生物。

> 专为 1~4 人团队协作设计，适合直播、录播整活、好友联机。

## 功能特性

| 特性 | 说明 |
|---|---|
| 持续麦克风监听 | 进入游戏即自动监听麦克风，无需手动触发 |
| 双服务商 ASR | 火山引擎（字节跳动）流式识别 / 阿里云流式识别，一键切换 |
| 智能匹配算法 | 精确匹配 → 别名匹配 → 云端热词表联合召回 |
| 跨玩家召唤 | 识别到生物名后，在**其他在线玩家**位置生成，而非说话者本人 |
| GUI 设置面板 | 基于 Cloth Config，所有参数无需退出游戏即可调整 |
| HUD 状态条 | 实时显示音量波形、识别文本、冷却进度、监听状态 |
| 完整容错 | WebSocket 断线自动重连、ASR 会话 10s 无变化自动重启、音频队列清理 |
| 凭据脱敏 | GUI 中凭证字段以掩码显示，绝不写入日志 |
| 快捷键 | 按 **V** 一键暂停 / 恢复监听 |
| 原版生态友好 | 不新增实体、不新增物品、不修改原版生物行为 |

## 工作流程

```
+------------------+   WebSocket   +--------------------+
|  客户端麦克风     | ------------> |  火山/阿里云 ASR   |
|  16kHz/16bit/单  |               |  流式语音识别 2.0  |
|  声道实时采集    | <------------ |  增量文本回调      |
+--------+---------+   TLS 1.3    +---------+----------+
         |                                  |
         |  音频帧 (Java Sound API)         |  识别文本 JSON
         v                                  v
+-------------------------------------------------------+
|              VoiceSummonClient (客户端主线程)           |
|  +---------------+  +---------------+  +------------+  |
|  | AudioCapture  |->| 增量匹配+防抖 |->| MobMatcher |  |
|  |  Manager      | | matchDelay 1s | | 精确/别名   | |
|  +---------------+  +---------------+  +------+-----+  |
|                                              | 命中     |
+----------------------------------------------+---------+
                                               |
                                      Custom C2S Packet
                               voicesummon:summon (play)
                                               |
                                               v
+-------------------------------------------------------+
|              VoiceSummonMod + SummonManager (服务端)    |
|  - 配置 mtime 热加载                                   |
|  - 范围 / 冷却 / 每次数量校验                          |
|  - 在其他玩家处 ServerLevel.addFreshEntity()            |
|  - 全员 ActionBar 广播召唤结果                         |
+-------------------------------------------------------+
```

## 前置要求

| 名称 | 最低版本 | 说明 |
|---|---|---|
| Minecraft | 1.21.10 | Yarn mappings `1.21.10+build.3` |
| Fabric Loader | `>= 0.19.3` | - |
| Fabric API | `>= 0.138.4+1.21.10` | 包含网络、命令、HUD 等钩子 |
| Fabric Language Kotlin | `>= 1.13.13+kotlin.2.4.10` | Kotlin 语言适配器 |
| Cloth Config | `>= 20.0.149` | GUI 设置面板依赖 |
| JDK | **21** | 构建与运行强制要求，JDK 8/17 不可用 |

## 安装

1. 将 `voicesummon-mod-Public-1.jar` 放入 `mods/` 目录
2. 首次启动游戏生成默认配置 `config/voicesummon.json`
3. 游戏内输入 `/voicesummon settings` 打开 GUI 设置面板
4. 填入云服务商凭证后保存，无需重启即生效

## 配置

### 云服务商凭证（必配）

模组支持两家服务商，在设置面板「语音识别 → Provider」中切换：

**火山引擎（字节跳动 / BYTEDANCE，推荐）**

| 字段 | 设置位置 | 默认值 |
|---|---|---|
| API Key (`X-Api-Key`) | `volcengineApiKey` | 空（自行填写） |
| Resource ID | `volcengineResourceId` | `volc.seedasr.sauc.duration` |
| 集群 / 实例 ID | `volcengineCluster` | 空（控制台绑定实例后填写） |
| 云端热词表 ID | `boostingTableId` | 空（强烈建议创建生物热词表） |

模型与计费：

| 模型 | 计费方式 | Resource ID |
|---|---|---|
| 豆包流式语音识别 2.0（推荐） | 小时版 | `volc.seedasr.sauc.duration` |
| 豆包流式语音识别 2.0 | 并发版 | `volc.seedasr.sauc.concurrent` |
| 豆包流式语音识别 1.0 | 小时版 | `volc.bigasr.sauc.duration` |

**阿里云（ALIYUN）**

| 字段 | 说明 |
|---|---|
| `aliyunAppKey` | 语音识别 App Key |
| `aliyunAccessKeyId` | AccessKey ID |
| `aliyunAccessKeySecret` | AccessKey Secret |
| `boostingTableId` | `vocabulary_id`（自定义热词表） |

### 召唤规则（常用）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `summonRange` | ∞ | 目标玩家距说话者的最大距离（方块） |
| `cooldownSeconds` | `10` | 两次召唤的冷却时间（秒） |
| `maxMobsPerSummon` | `5` | 一次匹配命中后生成的最大生物数 |
| `summonAtSpeakerIfNoTarget` | `true` | 范围内无其他玩家时，是否在说话者位置召唤 |
| `matchDelaySeconds` | `1.0` | 识别停顿多久后执行匹配（防抖） |
| `recognitionThreshold` | `0.6` | ASR 置信度阈值，低于此值忽略 |

### 其他

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `alwaysListening` | `true` | 进入游戏是否自动开启监听 |
| `microphoneName` | 空（系统默认） | 指定麦克风 |
| `enableNoiseReduction` | `true` | 客户端 FFT 软降噪 |
| `noiseReductionStrength` | `2` | 降噪强度 0~3 |
| `hudPosition` | `TOP_LEFT` | HUD 位置：`TOP_LEFT` / `TOP_RIGHT` / `BOTTOM_LEFT` / `BOTTOM_RIGHT` |
| `debugMode` | `false` | 输出详细日志（含 ASR 原始文本） |
| `logToFile` | `true` | 写入 `logs/voicesummon/voicesummon-YYYY-MM-DD.log` |

> 注意：`schemaVersion` 低于当前版本（6）时，旧配置文件将被**整体覆盖**为默认值。升级前请备份 `config/voicesummon.json`。

## 使用方法

1. 确认 HUD 左上角显示 **「● 监听中」**（绿色实心圆）
2. 直接对麦克风说生物名：
   ```
   "僵尸"   → 其他玩家位置生成僵尸
   "五只小白" → 其他玩家位置生成 5 只骷髅
   "两只小黑两只苦力怕" → 混合生成
   ```
3. 冷却中 HUD 显示倒计时；识别到文本会以半透明横幅即时显示
4. 按 **V** 暂停 / 恢复；输入 `/voicesummon off` 彻底关闭监听

### 命令

| 命令 | 说明 |
|---|---|
| `/voicesummon on` | 开启监听 |
| `/voicesummon off` | 关闭监听 |
| `/voicesummon status` | 查看状态 |
| `/voicesummon reload` | 重载配置（热加载） |
| `/voicesummon settings` | 打开 GUI 设置面板 |

### 别名支持

模组内置 80+ 生物映射 + 别名表，例如：

| 类型 | 示例 |
|---|---|
| 别名 | 小白 → 骷髅 / 小黑 → 末影人 / 猪人 → 僵尸猪灵 |
| 精确全名 | 循声守卫 / 监守者 / 坚守者 → Warden |

## 开发者：构建项目

### 环境准备

- JDK **21**（路径硬编码在 `gradle.properties` 的 `org.gradle.java.home`，首次使用请修改为本地路径）
- 无需单独安装 Gradle → 使用项目根目录 `./gradlew.bat`（Windows）自动下载 Gradle 9.5.1
- Gradle Wrapper 已配置**腾讯云镜像**，首次构建请耐心等待下载依赖

### 构建命令

```powershell
# Windows PowerShell
cd d:\idea\voicesummon-mod

# 全量构建（含测试 → 生成 remapped jar）
.\gradlew.bat build

# 产物位置：build\libs\voicesummon-mod-Public-1.jar

# 仅运行单元测试
.\gradlew.bat test
```

### 项目结构

```
src/main/kotlin/com/thiswuming/voicesummon/
├── VoiceSummonMod.kt          # 服务端入口：注册包、命令、事件
├── VoiceSummonClient.kt       # 客户端入口：音频采集、匹配调度
├── audio/                     # 16kHz 麦克风采集 + FFT 软降噪
├── recognition/               # 火山/阿里云 WebSocket 流式识别器
├── mob/                       # 生物映射 / 别名 / 匹配器
├── summon/                    # 服务端召唤逻辑 + 配置 mtime 热加载
├── network/                   # C2S 召唤包 + 客户端日志包
├── config/                    # Cloth Config 数据类 + schema 升级
├── gui/                       # 设置界面 + 掩码凭证输入框
├── keybind/                   # V 键监听开关
├── hud/                       # 音量条 / 状态 / 横幅渲染
├── command/                   # /voicesummon 子命令
└── util/                      # 日志代理 + 文本切分工具
```

### 构建注意事项

1. 使用 `net.fabricmc.fabric-loom-remap` 1.17-SNAPSHOT（非标准 fabric-loom），`loom {}` 块保持为空
2. 代码包名 `com.thiswuming.voicesummon`，`gradle.properties` 中 `maven_group=com.mango.voicesummon`（仅用于发布，不要改包名）
3. 额外依赖只有 **Gson 2.11.0** 和 **Cloth Config**；WebSocket 用 JDK 内置 `java.net.http.WebSocket`，无其他网络库

## FAQ

**Q：启动后 HUD 显示「API 未配置」？**
A：打开 `/voicesummon settings`，在「语音识别」分类填入火山引擎 API Key 和集群 ID，保存即可。

**Q：识别不准 / 误触发太多？**
A：
1. 建议在火山引擎**自学习平台**创建生物名热词表，把 `boostingTableId` 填到设置中（成本最低、效果最好）
2. 适当调高 `recognitionThreshold`（0.75~0.85）
3. 开启降噪并调整强度，或在安静环境使用

**Q：HUD 显示「连接中...」持续 20s 以上无识别结果？**
A：ASR 服务端初始化存在固有延迟，首次连接后前 20s 返回空文本是正常现象，不是 bug。等待后自动恢复。

**Q：怎么暂停监听？**
A：按 **V** 键，或 `/voicesummon off`。

**Q：服务端需要装这个模组吗？**
A：**需要**。客户端做语音识别，服务端负责接收 C2S 包并执行召唤；但服务端**不需要**配置任何 ASR 凭证，也不会访问麦克风。

## 许可证

[MIT License](LICENSE) © 2026 thiswuming (Wmsqaq)
