package com.thiswuming.voicesummon.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.thiswuming.voicesummon.util.ModLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ModConfig(
    
    @SerializedName("schemaVersion")
    var schemaVersion: Int = CURRENT_VERSION,

    
    @SerializedName("provider")
    var provider: String = "BYTEDANCE",

    
    @SerializedName("volcengineApiKey")
    var volcengineApiKey: String = "",

    
    @SerializedName("aliyunAppKey")
    var aliyunAppKey: String = "",

    
    @SerializedName("aliyunAccessKeyId")
    var aliyunAccessKeyId: String = "",

    
    @SerializedName("aliyunAccessKeySecret")
    var aliyunAccessKeySecret: String = "",

    @SerializedName("volcengineResourceId")
    var volcengineResourceId: String = "", 

    @SerializedName("volcengineCluster")
    var volcengineCluster: String = "", 

    
    @SerializedName("recognitionMode")
    var recognitionMode: String = "streaming",

    @SerializedName("recognitionThreshold")
    var recognitionThreshold: Float = 0.6f,

    @SerializedName("matchDelaySeconds")
    var matchDelaySeconds: Double = 1.0, 

    
    @SerializedName("boostingTableId")
    var boostingTableId: String = "",

    
    @SerializedName("summonRange")
    var summonRange: Double = Double.POSITIVE_INFINITY, 

    @SerializedName("maxMobsPerSummon")
    var maxMobsPerSummon: Int = 5, 

    @SerializedName("summonAtSpeakerIfNoTarget")
    var summonAtSpeakerIfNoTarget: Boolean = true,

    
    @SerializedName("alwaysListening")
    var alwaysListening: Boolean = true,

    @SerializedName("microphoneName")
    var microphoneName: String = "", 

    
    @SerializedName("enableNoiseReduction")
    var enableNoiseReduction: Boolean = true, 

    @SerializedName("noiseReductionStrength")
    var noiseReductionStrength: Int = 2, 

    @SerializedName("pauseKeyBinding")
    val pauseKeyBinding: String = "key.voicesummon.toggle",

    
    @SerializedName("debugMode")
    var debugMode: Boolean = false,

    @SerializedName("logToFile")
    var logToFile: Boolean = true,

    
    @SerializedName("hudPosition")
    var hudPosition: String = "TOP_LEFT"
) {
    companion object {
        
        const val CURRENT_VERSION: Int = 6

        private val logger = ModLogger.getLogger("ModConfig")
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            
            
            .serializeSpecialFloatingPointValues()
            .create()
        private val configDir: Path = Paths.get("config")
        private val configFile: Path = configDir.resolve("voicesummon.json")

        fun load(): ModConfig {
            try {
                
                if (!Files.exists(configDir)) {
                    Files.createDirectories(configDir)
                }

                
                if (!Files.exists(configFile)) {
                    logger.info("Config file not found, creating default config at {}", configFile)
                    save(ModConfig())
                    return ModConfig()
                }

                
                val content = Files.readString(configFile)

                
                val userTree = JsonParser.parseString(content).asJsonObject
                val fileVersion = userTree.get("schemaVersion")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                if (fileVersion < CURRENT_VERSION) {
                    logger.info(
                        "Config schema version {} < current {}, resetting to defaults (first launch of new version)",
                        fileVersion, CURRENT_VERSION
                    )
                    save(ModConfig())
                    return ModConfig()
                }

                val config = gson.fromJson(content, ModConfig::class.java)
                logger.info("Config loaded from {}", configFile)
                if (config.debugMode) {
                    logger.info("Config content: {}", gson.toJson(config))
                }
                return config
            } catch (e: Exception) {
                logger.error("Failed to load config, using defaults", e)
                return ModConfig()
            }
        }

        fun save(config: ModConfig) {
            try {
                if (!Files.exists(configDir)) {
                    Files.createDirectories(configDir)
                }
                val json = gson.toJson(config)
                Files.writeString(configFile, json)
                logger.info("Config saved to {}", configFile)
            } catch (e: Exception) {
                logger.error("Failed to save config", e)
            }
        }
    }
}