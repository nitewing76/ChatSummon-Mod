package com.nitelyfe.chatsummon

import com.nitelyfe.chatsummon.hud.VoiceHudOverlay
import com.nitelyfe.chatsummon.network.ChatSummonPacket
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

object ChatSummonClient : ClientModInitializer {

    private val logger = _root_ide_package_.com.nitelyfe.chatsummon.util.ModLogger.getLogger("ChatSummon-Client")

    lateinit var config: com.nitelyfe.chatsummon.config.ModConfig
        private set

    private var speechRecognizer: com.nitelyfe.chatsummon.recognition.SpeechRecognizer? = null
    private var audioCaptureManager: com.nitelyfe.chatsummon.audio.AudioCaptureManager? = null
    private var mobMatcher: com.nitelyfe.chatsummon.mob.MobMatcher? = null
    private var isListeningPaused: Boolean = false

    
    private var pendingText: String = ""
    private var lastRecognizedText: String = ""
    private var lastReceivedText: String = ""
    
    
    
    private var lastMatchedTextTail: String = ""
    
    @Volatile
    private var currentFullText: String = ""

    
    
    
    @Volatile
    private var lastTextChangedTime: Long = 0L
    private val stableRestartMs: Long = 10_000L
    @Volatile
    private var restartInProgress: Boolean = false

    
    
    
    @Volatile
    private var lastReceivedTime: Long = 0L
    private val noResultRestartMs: Long = 30_000L
    @Volatile
    private var asrWatchdog: ScheduledExecutorService? = null

    
    @Volatile
    private var matchScheduler: ScheduledExecutorService? = null
    @Volatile
    private var pendingMatchTask: java.util.concurrent.ScheduledFuture<*>? = null

    
    private val matchLock = ReentrantLock()

    @Volatile
    private var voiceDetectionEnabled: Boolean = true

    override fun onInitializeClient() {
        logger.info("ChatSummon client initializing...")

        
        config = _root_ide_package_.com.nitelyfe.chatsummon.config.ModConfig.load()

        
        _root_ide_package_.com.nitelyfe.chatsummon.util.ModLogger.init(config.debugMode, config.logToFile)

        
        mobMatcher = _root_ide_package_.com.nitelyfe.chatsummon.mob.MobMatcher()

        
        _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.init()

        
        _root_ide_package_.com.nitelyfe.chatsummon.keybind.VoiceKeyBinding.register()

        
        _root_ide_package_.com.nitelyfe.chatsummon.command.VoiceCommand.register()

        
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            
            _root_ide_package_.com.nitelyfe.chatsummon.util.ModLogger.clearLog()
            if (config.alwaysListening) {
                startVoiceRecognition()
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            stopVoiceRecognition()
        }

        logger.info("ChatSummon client initialized. Listening will start when entering a world.")
    }

    fun startVoiceRecognition() {
        
        val provider = config.provider.uppercase()
        val hasAliyunCreds = provider == "ALIYUN" &&
            config.aliyunAppKey.isNotBlank() &&
            config.aliyunAccessKeyId.isNotBlank() &&
            config.aliyunAccessKeySecret.isNotBlank()
        
        val hasApiKey = config.volcengineApiKey.isNotBlank() || config.aliyunAppKey.isNotBlank()
        if (!hasAliyunCreds && !hasApiKey) {
            logger.warn("API credentials not configured for provider '{}'. Voice recognition disabled.", provider)
            logger.warn("Please configure your speech provider credentials in the settings GUI or config file.")
            _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.no_api")
            return
        }

        
        speechRecognizer = when {
            provider == "ALIYUN" -> _root_ide_package_.com.nitelyfe.chatsummon.recognition.AliyunStreamRecognizer()
            config.recognitionMode == "streaming" -> _root_ide_package_.com.nitelyfe.chatsummon.recognition.VolcengineStreamRecognizer()
            else -> _root_ide_package_.com.nitelyfe.chatsummon.recognition.VolcengineFlashRecognizer()
        }

        
        val recognizerConfig = _root_ide_package_.com.nitelyfe.chatsummon.recognition.VolcengineConfig(
            apiKey = config.volcengineApiKey,
            resourceId = config.volcengineResourceId,
            cluster = config.volcengineCluster,
            boostingTableId = config.boostingTableId,
            aliyunAppKey = config.aliyunAppKey,
            aliyunAccessKeyId = config.aliyunAccessKeyId,
            aliyunAccessKeySecret = config.aliyunAccessKeySecret
        )

        speechRecognizer?.start(recognizerConfig) { result ->
            onRecognitionResult(result)
        }

        
        audioCaptureManager = _root_ide_package_.com.nitelyfe.chatsummon.audio.AudioCaptureManager(
            onAudioData = { audioData ->
                if (!isListeningPaused) {
                    speechRecognizer?.feedAudio(audioData)
                }
            },
            noiseReductionEnabled = config.enableNoiseReduction,
            noiseReductionStrength = config.noiseReductionStrength
        )
        val mixer = if (config.microphoneName.isNotBlank()) {
            _root_ide_package_.com.nitelyfe.chatsummon.audio.AudioCaptureManager.getMicrophoneByName(config.microphoneName)
        } else {
            null
        }
        if (config.microphoneName.isNotBlank() && mixer == null) {
            logger.warn("Configured microphone '{}' not found, using system default", config.microphoneName)
        }
        audioCaptureManager?.start(mixer)

        startAsrWatchdog()

        _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("监听中")
        logger.info("Voice recognition started. Mode: {}", config.recognitionMode)
    }

    fun stopVoiceRecognition() {
        stopAsrWatchdog()

        audioCaptureManager?.stop()
        speechRecognizer?.stop()

        
        pendingMatchTask?.cancel(false)
        pendingMatchTask = null
        matchScheduler?.shutdownNow()
        matchScheduler = null
        
        matchLock.lock()
        try {
            pendingText = ""
            lastRecognizedText = ""
            lastReceivedText = ""
            lastMatchedTextTail = ""
            currentFullText = ""
            lastTextChangedTime = 0L
            lastReceivedTime = 0L
            restartInProgress = false
        } finally {
            matchLock.unlock()
        }

        _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.stopped")
        logger.info("Voice recognition stopped.")
    }

    
    private fun startAsrWatchdog() {
        stopAsrWatchdog()
        lastReceivedTime = System.currentTimeMillis()
        asrWatchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "chatsummon-asr-watchdog").apply { isDaemon = true }
        }
        asrWatchdog?.scheduleAtFixedRate({
            try {
                if (restartInProgress) return@scheduleAtFixedRate
                if (!voiceDetectionEnabled) return@scheduleAtFixedRate
                val last = lastReceivedTime
                if (last == 0L) return@scheduleAtFixedRate
                val elapsed = System.currentTimeMillis() - last
                if (elapsed > noResultRestartMs) {
                    logger.warn(
                        "ASR no response for {}ms (last result at {}), restarting ASR session",
                        elapsed, last
                    )
                    restartAsrSessionAsync("no-response-watchdog")
                }
            } catch (e: Exception) {
                logger.error("ASR watchdog error", e)
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    private fun stopAsrWatchdog() {
        asrWatchdog?.shutdownNow()
        asrWatchdog = null
    }

    fun togglePause() {
        isListeningPaused = !isListeningPaused
        if (isListeningPaused) {
            _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.paused")
            logger.info("Voice recognition paused.")
        } else {
            _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.listening")
            logger.info("Voice recognition resumed.")
        }
    }

    fun isPaused(): Boolean = isListeningPaused

    
    fun isVoiceDetectionEnabled(): Boolean = voiceDetectionEnabled

    
    fun setVoiceDetectionEnabled(enabled: Boolean) {
        if (voiceDetectionEnabled == enabled) return
        voiceDetectionEnabled = enabled

        if (enabled) {
            startVoiceRecognition()
            _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.listening")
            logger.info("Voice detection enabled")
        } else {
            stopVoiceRecognition()
            isListeningPaused = false
            _root_ide_package_.com.nitelyfe.chatsummon.hud.VoiceHudOverlay.showStatus("hud.status.disabled")
            logger.info("Voice detection disabled")
        }
    }

    
    fun reloadConfig() {
        
        val wasEnabled = voiceDetectionEnabled
        stopVoiceRecognition()

        
        config = _root_ide_package_.com.nitelyfe.chatsummon.config.ModConfig.load()

        
        if (wasEnabled) {
            startVoiceRecognition()
        }
        logger.info("Config reloaded")
    }

    fun getCurrentVolume(): Float {
        return audioCaptureManager?.getCurrentVolume() ?: 0f
    }

    
    fun applyNoiseReductionFromConfig(): Boolean {
        val acm = audioCaptureManager ?: return false
        val wantEnabled = config.enableNoiseReduction
        val wantStrength = config.noiseReductionStrength.coerceIn(1, 3)
        val alreadyEnabled = acm.isNoiseReductionEnabled()
        val currentStrength = acm.getNoiseReductionStrength()
        if (wantEnabled == alreadyEnabled && wantStrength == currentStrength) {
            return false
        }
        acm.setNoiseReduction(wantEnabled, wantStrength)
        return true
    }

    
    fun setMicrophone(name: String): Boolean {
        val trimmed = name.trim()

        
        if (trimmed.isNotBlank() && _root_ide_package_.com.nitelyfe.chatsummon.audio.AudioCaptureManager.getMicrophoneByName(trimmed) == null) {
            return false
        }

        config.microphoneName = trimmed
        _root_ide_package_.com.nitelyfe.chatsummon.config.ModConfig.save(config)

        
        val wasEnabled = voiceDetectionEnabled
        if (wasEnabled) {
            audioCaptureManager?.stop()
            audioCaptureManager?.start(
                if (trimmed.isNotBlank()) _root_ide_package_.com.nitelyfe.chatsummon.audio.AudioCaptureManager.getMicrophoneByName(trimmed) else null
            )
        }
        logger.info("Microphone changed to: {}", trimmed.ifBlank { "system default" })
        return true
    }

    
    fun logHudToggle(visible: Boolean) {
        logger.info("HUD visibility toggled: {}", if (visible) "shown" else "hidden")
    }

    
    fun logSettingsOpenError(e: Exception) {
        logger.error("Failed to open settings screen", e)
    }

    
    fun onConfigSaved(vendorChanged: Boolean = false) {
        
        val nrChanged = applyNoiseReductionFromConfig()
        if (nrChanged) {
            logger.info(
                "Noise reduction hot-applied: enabled={}, strength={}",
                config.enableNoiseReduction, config.noiseReductionStrength
            )
        }

        
        if (config.alwaysListening && voiceDetectionEnabled) {
            
            stopVoiceRecognition()
            startVoiceRecognition()
        } else if (!config.alwaysListening) {
            
            if (audioCaptureManager?.isRunning() == true) {
                stopVoiceRecognition()
            }
        }
        
        if (vendorChanged) {
            sendConfigAppliedNotice()
        }
        logger.info("Config applied")
    }

    
    private fun sendConfigAppliedNotice() {
        val player = MinecraftClient.getInstance().player ?: return
        val provider = config.provider.uppercase()
        val providerName = if (provider == "ALIYUN") "Aliyun" else "ByteDance"
        val authMode = when {
            provider == "ALIYUN" ->
                if (config.aliyunAppKey.isNotBlank()) "AppKey" else "未配置"
            config.volcengineApiKey.isNotBlank() -> "新API Key"
            else -> "未配置"
        }
        player.sendMessage(
            Text.empty()
                .append(Text.literal("[修改成功] ").formatted(Formatting.GOLD))
                .append(Text.literal("厂商："))
                .append(Text.literal(providerName).formatted(Formatting.AQUA))
                .append(Text.literal("｜").formatted(Formatting.GRAY))
                .append(Text.literal(authMode).formatted(Formatting.YELLOW)),
            false
        )
    }

    private fun onRecognitionResult(result: com.nitelyfe.chatsummon.recognition.RecognitionResult) {
        if (!result.isFinal) {
            
            logger.debug("Partial recognition: '{}'", result.text)
            return
        }

        if (result.confidence < config.recognitionThreshold) {
            logger.debug("Recognition confidence too low: {} < {}", result.confidence, config.recognitionThreshold)
            return
        }

        logger.info("Recognition result: text='{}', confidence={}", result.text, result.confidence)

        
        
        val text = result.text
        
        
        lastReceivedTime = System.currentTimeMillis()

        
        val hasAddition: Boolean
        matchLock.lock()
        try {
            
            currentFullText = text

            if (text == lastReceivedText) {
                
                
                
                
                val now = System.currentTimeMillis()
                if (lastTextChangedTime > 0 &&
                    !restartInProgress &&
                    now - lastTextChangedTime > stableRestartMs &&
                    voiceDetectionEnabled
                ) {
                    logger.info(
                        "Recognition text stable for {}ms ('{}'), restarting ASR session to force utterance break",
                        stableRestartMs, text
                    )
                    restartInProgress = true
                    restartAsrSessionAsync("text-stable-timeout")
                }
                return
            }
            lastReceivedText = text
            lastTextChangedTime = System.currentTimeMillis()

            
            
            
            
            val addition = if (lastRecognizedText.isEmpty()) {
                text
            } else if (text.startsWith(lastRecognizedText)) {
                text.substring(lastRecognizedText.length)
            } else {
                
                
                
                text.substring(commonPrefixLength(lastRecognizedText, text))
            }

            if (addition.isBlank()) {
                
                return
            }

            pendingText += addition
            hasAddition = true
        } finally {
            matchLock.unlock()
        }

        if (hasAddition) {
            scheduleMatch()
        }
    }

    
    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    
    private fun scheduleMatch() {
        
        
        if (!voiceDetectionEnabled) return

        val delayMs = (config.matchDelaySeconds * 1000).toLong()
        pendingMatchTask?.cancel(false)

        var sched = matchScheduler
        if (sched == null || sched.isShutdown) {
            sched = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "voice-summon-match").apply { isDaemon = true }
            }
            matchScheduler = sched
        }
        pendingMatchTask = sched.schedule({
            matchPendingAndSummon()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    
    private fun restartAsrSessionAsync(reason: String) {
        Thread({
            try {
                logger.info("=== ASR session restart begin (triggered by {}) ===", reason)
                stopVoiceRecognition()
                logger.info("=== ASR session stopped, reopening new session ===")
                startVoiceRecognition()
                logger.info("=== ASR session restart end ===")
            } catch (e: Exception) {
                logger.error("=== ASR session restart failed ===", e)
            } finally {
                restartInProgress = false
            }
        }, "asr-session-restart").apply {
            isDaemon = true
            start()
        }
    }

    
    private fun matchPendingAndSummon() {
        
        
        val textToMatch: String
        val contextPrefix: String
        
        val maxKeywordLen = mobMatcher?.maxKeywordLength ?: 0
        val takeLen = (maxKeywordLen - 1).coerceAtLeast(0)
        matchLock.lock()
        try {
            if (pendingText.isBlank()) return
            textToMatch = pendingText
            pendingText = ""

            
            
            
            
            contextPrefix = if (lastMatchedTextTail.length > takeLen) {
                lastMatchedTextTail.substring(lastMatchedTextTail.length - takeLen)
            } else {
                lastMatchedTextTail
            }
        } finally {
            matchLock.unlock()
        }

        val matchResults = mobMatcher?.matchAllWithContext(contextPrefix, textToMatch).orEmpty()
        if (matchResults.isEmpty()) {
            
            
            
            
            
            val isDeferral = mobMatcher?.isTrailingPrefixDeferral(textToMatch) == true
            matchLock.lock()
            try {
                if (isDeferral) {
                    
                    pendingText = textToMatch + pendingText
                } else {
                    
                    lastRecognizedText = currentFullText
                    lastMatchedTextTail = if (textToMatch.length > takeLen) {
                        textToMatch.substring(textToMatch.length - takeLen)
                    } else {
                        textToMatch
                    }
                }
            } finally {
                matchLock.unlock()
            }
            logger.debug("No mob matched for text: '{}' (deferred={})", textToMatch, isDeferral)
            return
        }

        
        
        
        
        matchLock.lock()
        try {
            lastRecognizedText = currentFullText
            lastMatchedTextTail = if (textToMatch.length > takeLen) {
                textToMatch.substring(textToMatch.length - takeLen)
            } else {
                textToMatch
            }
        } finally {
            matchLock.unlock()
        }

        
        val uniqueNames = matchResults.map { it.matchedName }.distinct()
        val resultsByName = matchResults.associateBy { it.matchedName }
        val namesDisplay = uniqueNames.joinToString("、")

        logger.info("Mob matched: text='{}' -> {} (count={})",
            textToMatch, namesDisplay, uniqueNames.size)

        
        VoiceHudOverlay.showRecognitionText(namesDisplay)

        
        
        MinecraftClient.getInstance().execute {
            if (MinecraftClient.getInstance().player != null) {
                ClientPlayNetworking.send(
                    ChatSummonPacket(
                        mobNames = uniqueNames,
                        matchType = resultsByName.values.first().matchType.name,
                        confidence = resultsByName.values.maxOf { it.confidence }
                    )
                )
                logger.info("Summon packet sent: mobNames={}", namesDisplay)
            }
        }
    }
}