package com.thiswuming.voicesummon.gui

import com.thiswuming.voicesummon.VoiceSummonClient
import com.thiswuming.voicesummon.audio.AudioCaptureManager
import com.thiswuming.voicesummon.config.ModConfig
import com.thiswuming.voicesummon.util.ModLogger
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import java.util.Optional
import java.util.function.Supplier


object ConfigScreen {

    private val logger = ModLogger.getLogger("ConfigScreen")

    
    private const val DEFAULT_MIC = "系统默认"

    
    fun create(parent: Screen?): Screen {
        return try {
            buildScreen(parent)
        } catch (e: Exception) {
            logger.error("Failed to build settings screen", e)
            
            object : Screen(Text.translatable("gui.title")) {
                override fun init() {
                    super.init()
                }
            }
        }
    }

    private fun buildScreen(parent: Screen?): Screen {
        val config = VoiceSummonClient.config
        
        
        val vendorSnapshot = VendorState(
            provider = config.provider,
            volcengineApiKey = config.volcengineApiKey,
            aliyunAppKey = config.aliyunAppKey,
            aliyunAccessKeyId = config.aliyunAccessKeyId,
            aliyunAccessKeySecret = config.aliyunAccessKeySecret
        )
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.translatable("gui.title"))
            .setSavingRunnable {
                ModConfig.save(config)
                val vendorChanged = vendorSnapshot.differsFrom(
                    config.provider, config.volcengineApiKey, config.aliyunAppKey,
                    config.aliyunAccessKeyId, config.aliyunAccessKeySecret
                )
                VoiceSummonClient.onConfigSaved(vendorChanged)
                logger.info("Config saved via GUI")
            }

        val entryBuilder = builder.entryBuilder()
        val general = builder.getOrCreateCategory(Text.translatable("gui.category.general"))
        val recognition = builder.getOrCreateCategory(Text.translatable("gui.category.recognition"))
        val summon = builder.getOrCreateCategory(Text.translatable("gui.category.summon"))
        val volcengineApi = builder.getOrCreateCategory(Text.translatable("gui.category.volcengine_api"))
        val aliyunApi = builder.getOrCreateCategory(Text.translatable("gui.category.aliyun_api"))
        val clientHud = builder.getOrCreateCategory(Text.translatable("gui.category.client_hud"))

        
        general.addEntry(
            entryBuilder.startBooleanToggle(Text.translatable("gui.always_listening"), config.alwaysListening)
                .setDefaultValue(config.alwaysListening)
                .setTooltip(Text.translatable("gui.always_listening.tooltip"))
                .setSaveConsumer { value -> config.alwaysListening = value }
                .build()
        )

        
        
        
        val micOptions = listOf(DEFAULT_MIC) + AudioCaptureManager.listMicrophones().map { it.mixerInfo.name }
        general.addEntry(
            entryBuilder.startDropdownMenu(
                Text.translatable("gui.microphone"),
                config.microphoneName.ifBlank { DEFAULT_MIC },
                { it } 
            )
                .setSelections(micOptions)
                .setSuggestionMode(true) 
                .setDefaultValue(config.microphoneName.ifBlank { DEFAULT_MIC })
                .setTooltip(Text.translatable("gui.microphone.tooltip"))
                .setSaveConsumer { name ->
                    val target = if (name == DEFAULT_MIC) "" else name
                    config.microphoneName = target
                    
                    VoiceSummonClient.setMicrophone(target)
                }
                .build()
        )

        
        general.addEntry(
            entryBuilder.startBooleanToggle(Text.translatable("gui.enable_noise_reduction"), config.enableNoiseReduction)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("gui.enable_noise_reduction.tooltip"))
                .setSaveConsumer { value -> config.enableNoiseReduction = value }
                .build()
        )

        
        val nrLevels = intArrayOf(1, 2, 3)
        general.addEntry(
            entryBuilder.startSelector(
                Text.translatable("gui.noise_reduction_strength"),
                nrLevels.toTypedArray(),
                config.noiseReductionStrength.coerceIn(1, 3)
            )
                .setDefaultValue(2)
                .setNameProvider { level -> Text.translatable("gui.noise_reduction_strength.$level") }
                .setTooltip(Text.translatable("gui.noise_reduction_strength.tooltip"))
                .setSaveConsumer { level -> config.noiseReductionStrength = level }
                .build()
        )

        general.addEntry(
            entryBuilder.startBooleanToggle(Text.translatable("gui.debug_mode"), config.debugMode)
                .setDefaultValue(config.debugMode)
                .setTooltip(Text.translatable("gui.debug_mode.tooltip"))
                .setSaveConsumer { value -> config.debugMode = value }
                .build()
        )

        
        val hudPositions = arrayOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_RIGHT", "SPLIT")
        clientHud.addEntry(
            entryBuilder.startSelector(
                Text.translatable("gui.hud_position"),
                hudPositions,
                config.hudPosition
            )
                .setDefaultValue("TOP_LEFT")
                .setNameProvider { key -> Text.translatable("gui.hud_position.$key") }
                .setTooltip(Text.translatable("gui.hud_position.tooltip"))
                .setSaveConsumer { value -> config.hudPosition = value }
                .build()
        )

        
        
        volcengineApi.addEntry(
            MaskedStringListEntry(
                Text.translatable("gui.api_key"),
                config.volcengineApiKey,
                Text.translatable("text.cloth-config.reset_value"),
                Supplier { "" },
                { value -> config.volcengineApiKey = value },
                Supplier { Optional.of(arrayOf(Text.translatable("gui.api_key.tooltip"))) }
            )
        )

        
        val knownModels = linkedMapOf(
            "volc.seedasr.sauc.duration" to "gui.model.seedasr_duration",
            "volc.seedasr.sauc.concurrent" to "gui.model.seedasr_concurrent"
        )
        val currentModel = config.volcengineResourceId
        val modelOptions = if (knownModels.containsKey(currentModel)) {
            knownModels.keys.toTypedArray()
        } else {
            
            (knownModels.keys + currentModel).toTypedArray()
        }
        volcengineApi.addEntry(
            entryBuilder.startSelector(
                Text.translatable("gui.resource_id"),
                modelOptions,
                currentModel
            )
                .setDefaultValue("volc.seedasr.sauc.duration")
                .setNameProvider { key ->
                    val langKey = knownModels[key]
                    if (langKey != null) Text.translatable(langKey) else Text.literal(key)
                }
                .setTooltip(Text.translatable("gui.resource_id.tooltip"))
                .setSaveConsumer { value -> config.volcengineResourceId = value }
                .build()
        )

        
        volcengineApi.addEntry(
            entryBuilder.startTextDescription(
                Text.translatable("gui.model.restart_tip")
                    .styled { it.withColor(net.minecraft.util.Formatting.RED).withBold(true) }
            ).build()
        )

        
        volcengineApi.addEntry(
            entryBuilder.startTextField(Text.translatable("gui.cluster"), config.volcengineCluster)
                .setDefaultValue(config.volcengineCluster)
                .setTooltip(Text.translatable("gui.cluster.tooltip"))
                .setSaveConsumer { value -> config.volcengineCluster = value }
                .build()
        )

        
        volcengineApi.addEntry(
            entryBuilder.startTextField(Text.translatable("gui.boosting_table_id"), config.boostingTableId)
                .setDefaultValue(config.boostingTableId)
                .setTooltip(Text.translatable("gui.boosting_table_id.tooltip"))
                .setSaveConsumer { value -> config.boostingTableId = value }
                .build()
        )

        
        
        aliyunApi.addEntry(
            MaskedStringListEntry(
                Text.translatable("gui.aliyun_app_key"),
                config.aliyunAppKey,
                Text.translatable("text.cloth-config.reset_value"),
                Supplier { "" },
                { value -> config.aliyunAppKey = value },
                Supplier { Optional.of(arrayOf(Text.translatable("gui.aliyun_app_key.tooltip"))) }
            )
        )

        
        aliyunApi.addEntry(
            MaskedStringListEntry(
                Text.translatable("gui.aliyun_access_key_id"),
                config.aliyunAccessKeyId,
                Text.translatable("text.cloth-config.reset_value"),
                Supplier { "" },
                { value -> config.aliyunAccessKeyId = value },
                Supplier { Optional.of(arrayOf(Text.translatable("gui.aliyun_access_key_id.tooltip"))) }
            )
        )

        
        aliyunApi.addEntry(
            MaskedStringListEntry(
                Text.translatable("gui.aliyun_access_key_secret"),
                config.aliyunAccessKeySecret,
                Text.translatable("text.cloth-config.reset_value"),
                Supplier { "" },
                { value -> config.aliyunAccessKeySecret = value },
                Supplier { Optional.of(arrayOf(Text.translatable("gui.aliyun_access_key_secret.tooltip"))) }
            )
        )

        
        
        recognition.addEntry(
            entryBuilder.startSelector(
                Text.translatable("gui.provider"),
                arrayOf("BYTEDANCE", "ALIYUN"),
                config.provider
            )
                .setDefaultValue("BYTEDANCE")
                .setNameProvider { key ->
                    when (key) {
                        "ALIYUN" -> Text.translatable("gui.provider.aliyun")
                        else -> Text.translatable("gui.provider.bytedance")
                    }
                }
                .setTooltip(Text.translatable("gui.provider.tooltip"))
                .setSaveConsumer { value -> config.provider = value }
                .build()
        )

        recognition.addEntry(
            entryBuilder.startFloatField(Text.translatable("gui.recognition_threshold"), config.recognitionThreshold)
                .setDefaultValue(config.recognitionThreshold)
                .setMin(0f)
                .setMax(1f)
                .setTooltip(Text.translatable("gui.recognition_threshold.tooltip"))
                .setSaveConsumer { value -> config.recognitionThreshold = value }
                .build()
        )

        recognition.addEntry(
            entryBuilder.startFloatField(Text.translatable("gui.match_delay"), config.matchDelaySeconds.toFloat())
                .setDefaultValue(config.matchDelaySeconds.toFloat())
                .setMin(0f)
                .setMax(10f)
                .setTooltip(Text.translatable("gui.match_delay.tooltip"))
                .setSaveConsumer { value -> config.matchDelaySeconds = Math.round(value * 10) / 10.0 }
                .build()
        )

        
        summon.addEntry(
            entryBuilder.startLongField(Text.translatable("gui.summon_range"), config.summonRange.toLong())
                .setDefaultValue(config.summonRange.toLong())
                .setMin(0L)
                .setTooltip(Text.translatable("gui.summon_range.tooltip"))
                .setSaveConsumer { value -> config.summonRange = value.toDouble() }
                .build()
        )

        summon.addEntry(
            entryBuilder.startIntField(Text.translatable("gui.max_mobs"), config.maxMobsPerSummon)
                .setDefaultValue(config.maxMobsPerSummon)
                .setMin(1)
                .setMax(50)
                .setTooltip(Text.translatable("gui.max_mobs.tooltip"))
                .setSaveConsumer { value -> config.maxMobsPerSummon = value }
                .build()
        )

        summon.addEntry(
            entryBuilder.startBooleanToggle(Text.translatable("gui.summon_at_speaker"), config.summonAtSpeakerIfNoTarget)
                .setDefaultValue(config.summonAtSpeakerIfNoTarget)
                .setTooltip(Text.translatable("gui.summon_at_speaker.tooltip"))
                .setSaveConsumer { value -> config.summonAtSpeakerIfNoTarget = value }
                .build()
        )

        return builder.build()
    }
}


private data class VendorState(
    val provider: String,
    val volcengineApiKey: String,
    val aliyunAppKey: String,
    val aliyunAccessKeyId: String,
    val aliyunAccessKeySecret: String
) {
    fun differsFrom(
        provider: String,
        volcengineApiKey: String,
        aliyunAppKey: String,
        aliyunAccessKeyId: String,
        aliyunAccessKeySecret: String
    ): Boolean = this.provider != provider ||
        this.volcengineApiKey != volcengineApiKey ||
        this.aliyunAppKey != aliyunAppKey ||
        this.aliyunAccessKeyId != aliyunAccessKeyId ||
        this.aliyunAccessKeySecret != aliyunAccessKeySecret
}