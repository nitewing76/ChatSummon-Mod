# VoiceSummon 语音召唤模组

通过语音识别控制生物召唤的 Minecraft Fabric 模组。模组持续监听玩家语音，当识别到生物名称时，自动在其他玩家附近生成对应生物。

> **注意：** 本模组仅支持**客户端**使用语音功能；服务端只需要接收召唤指令并生成生物（服务端无需配置语音 API）。

## 功能特性

- **持续监听**：模组启动后自动持续监听麦克风，无需手动触发
- **火山引擎语音识别**：基于火山引擎大模型流式语音识别，实时识别玩家语音
- **生物识别**：支持精确匹配、模糊匹配、别名匹配，提高识别准确率
- **跨玩家召唤**：识别到生物名称后，在其他玩家位置生成生物
- **可配置**：灵敏度、召唤范围、冷却时间等参数均可配置
- **HUD 显示**：实时显示监听状态、音量、识别结果、冷却时间
- **完整错误处理**：网络断开自动重连，API 错误友好提示

## 前置要求

- Minecraft **1.21.10**
- Fabric Loader **>= 0.18.4**
- Fabric API
- Fabric Language Kotlin
- **Cloth Config**（GUI 设置界面）
- Java **21+**
- **火山引擎**语音识别服务（需自行申请）

## 安装

1. 将 `voicesummon-mod-<version>.jar` 放入 `mods` 文件夹
2. 启动游戏生成默认配置文件 `config/voicesummon.json`
3. 编辑配置文件，填入火山引擎 API 凭证
4. 重启游戏（或重新加载配置）

## 配置说明

配置文件位于 `config/voicesummon.json`：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `volcengineApiKey` | String | 内置 | 新版控制台 API Key（X-Api-Key，唯一鉴权方式，旧版已移除） |
| `volcengineResourceId` | String | `volc.seedasr.sauc.duration` | 语音识别资源 ID（豆包流式语音识别模型2.0 小时版） |
| `volcengineCluster` | String | `Doubao_Seed_ASR_Streaming_2.02000000894305562242` | 业务集群（实例 ID） |
| `boostingTableId` | String | `d1246823-8cff-4587-a320-745e26ea1216` | 云端热词表 ID（字节跳动 boosting_table_id / 阿里云 vocabulary_id，按所选服务商填写） |
| `recognitionMode` | String | `streaming` | 识别模式：`streaming`（流式）/ `flash`（极速版） |
| `recognitionThreshold` | Float | `0.6` | 识别置信度阈值（0~1，过低则忽略） |
| `summonRange` | Double | `∞` | 召唤范围（距目标玩家的最大距离，默认无限、不限制维度） |
| `cooldownSeconds` | Int | `10` | 召唤冷却时间（秒） |
| `maxMobsPerSummon` | Int | `1` | 每次召唤最大生物数量 |
| `summonAtSpeakerIfNoTarget` | Boolean | `true` | 范围内无其他玩家时，是否在说话者位置召唤 |
| `alwaysListening` | Boolean | `true` | 是否始终监听 |
| `microphoneName` | String | `""` | 使用的麦克风名称（空为系统默认） |
| `pauseKeyBinding` | String | `key.voicesummon.toggle` | 暂停/恢复按键 |
| `debugMode` | Boolean | `false` | 调试模式（输出详细日志） |
| `logToFile` | Boolean | `true` | 是否写入文件日志 |

## 使用方法

1. **启动游戏**，确认 HUD 显示"监听中"（绿色圆点）
2. **说出生物名称**，例如"僵尸"、"骷髅"、"苦力怕"
3. 模组识别后会在**其他在线玩家**附近生成对应生物
4. 如果范围内没有其他玩家，会在你附近生成（可配置）
5. 按 **V 键** 暂停/恢复语音监听

### 指令

| 指令 | 说明 |
|------|------|
| `/voicesummon on` | 开启语音检测 |
| `/voicesummon off` | 关闭语音检测 |
| `/voicesummon status` | 查看当前语音检测状态 |
| `/voicesummon reload` | 重新加载配置 |
| `/voicesummon settings` | **打开 GUI 设置界面** |

### 设置界面

大多数设置（麦克风、识别灵敏度、召唤范围、冷却时间等）通过 **GUI 设置界面** 调整，无需手动编辑配置文件：

- 输入 `/voicesummon settings` 打开设置界面
- 界面分为「通用」「语音识别」「召唤」三个分类
- 修改后点击「保存」自动生效并写入配置文件

GUI 中可配置：
- **麦克风**：下拉框选择输入的麦克风（含"系统默认"）
- **始终监听**、**调试模式**：开关
- **识别置信度阈值**：0~1
- **云端热词表 ID**：按所选服务商填对应平台的云端热词表 ID
- **召唤范围**、**冷却时间**、**每次最大生物数**：数值
- **无目标时在说话者位置召唤**：开关

也可以直接编辑 `config/voicesummon.json`，两者等效。

### 支持的生物

模组内置了 80+ 种 Minecraft 生物的中文名称映射，包括：

- **敌对生物**：僵尸、骷髅、苦力怕、末影人、蜘蛛、史莱姆、烈焰人、恶魂、凋零、猪灵、女巫、守卫者等
- **中立/被动**：铁傀儡、雪傀儡、狼、猫、海豚、山羊、熊猫、狐狸等
- **牲畜**：牛、羊、猪、鸡、马、驴、骡、兔子、鱿鱼等
- **村民相关**：村民、流浪商人
- **BOSS**：末影龙、凋零

### 别名支持

- "小黑" → 末影人
- "小白" → 骷髅
- "猪人" → 僵尸猪灵

## 火山引擎 API 配置

### 1. 申请服务

1. 访问 [火山引擎控制台](https://console.volcengine.com/speech/)
2. 开通**语音识别**服务（流式语音识别）
3. 创建应用，获取 `App ID` / `Access Key`

### 2. 选择语音识别模型

模组通过 `volcengineResourceId` 选择语音识别模型，默认使用**豆包流式语音识别模型 1.0**。如需使用 **模型 2.0**，修改资源 ID 即可（接口一致，无需改代码）：

| 模型 | 计费方式 | 资源 ID |
|------|---------|---------|
| 豆包流式语音识别 1.0 | 小时版 | `volc.bigasr.sauc.duration` |
| 豆包流式语音识别 1.0 | 并发版 | `volc.bigasr.sauc.concurrent` |
| 豆包流式语音识别 2.0 | 小时版 | `volc.seedasr.sauc.duration` |
| 豆包流式语音识别 2.0 | 并发版 | `volc.seedasr.sauc.concurrent` |

> 具体开通哪个模型请以控制台实际开通的服务为准；未开通对应模型时调用会返回鉴权错误。

### 3. 填写配置

将获取到的凭证填入 `config/voicesummon.json`：

```json
{
  "volcengineAppId": "你的AppID",
  "volcengineAccessKey": "你的AccessKey",
  "volcengineResourceId": "volc.bigasr.sauc.duration",
  "volcengineCluster": "volcengine_streaming_common"
}
```

> 模组默认使用**流式语音识别大模型**（`volc.bigasr.sauc.duration`，小时版），如需切换到豆包2.0将 `volcengineResourceId` 改为 `volc.seedasr.sauc.duration` 即可。

## 日志

日志文件位于 `logs/voicesummon/voicesummon-<日期>.log`，包含：
- 识别结果（文字 + 置信度）
- 召唤操作（玩家、生物、目标位置）
- 错误信息和堆栈
- 网络连接/重连事件

## 常见问题

**Q：启动后 HUD 显示"API未配置"？**
A：请在 `config/voicesummon.json` 中填写火山引擎 API 凭证。

**Q：识别不准确？**
A：可以尝试：
- 提高 `recognitionThreshold` 阈值
- 在所选服务商平台创建云端热词表，并在设置界面「云端热词表 ID」填入对应 ID（字节跳动 boosting_table_id / 阿里云 vocabulary_id）
- 在安静环境下使用

**Q：麦克风无响应？**
A：检查系统麦克风权限，确保其他程序未占用麦克风。

**Q：如何减少误触发？**
A：按 V 键暂停监听，或提高识别阈值。

## 许可证

MIT License