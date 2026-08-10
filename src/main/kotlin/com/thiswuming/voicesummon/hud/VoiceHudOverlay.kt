package com.thiswuming.voicesummon.hud

import com.thiswuming.voicesummon.VoiceSummonClient
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.Text
import net.minecraft.util.Identifier


object VoiceHudOverlay {

    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()

    private var statusKey: String = "hud.status.listening"

    
    @Volatile
    private var recognitionText: String = ""
    @Volatile
    private var recognitionTextTimer: Long = 0L
    private val textDisplayDurationMs: Long = 3000L

    @Volatile
    private var errorText: String = ""
    @Volatile
    private var errorTextTimer: Long = 0L
    private val errorDisplayDurationMs: Long = 5000L

    
    private const val VOLUME_GAIN = 2.5f

    @Volatile
    private var hudVisible: Boolean = true

    
    fun isHudVisible(): Boolean = hudVisible

    
    fun toggleHud() {
        hudVisible = !hudVisible
        VoiceSummonClient.logHudToggle(hudVisible)
    }

    fun init() {
        HudRenderCallback.EVENT.register { context, tickCounter ->
            onRenderHud(context, tickCounter)
        }
    }

    
    fun showStatus(key: String) {
        statusKey = key
    }

    
    fun showRecognitionText(text: String) {
        recognitionText = text
        recognitionTextTimer = System.currentTimeMillis()
    }

    
    fun showError(text: String) {
        errorText = text
        errorTextTimer = System.currentTimeMillis()
    }

    private fun onRenderHud(context: DrawContext, tickCounter: RenderTickCounter) {
        if (client.player == null) return
        if (!hudVisible) return

        val now = System.currentTimeMillis()

        
        drawAuthorBanner(context, now)

        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val tr = client.textRenderer
        val pos = hudPosition()

        
        val recognizing = recognitionText.isNotEmpty() && now - recognitionTextTimer < textDisplayDurationMs
        val statusKeyEffective = if (recognizing) "hud.status.recognizing" else statusKey
        val statusText = Text.literal("● ").append(Text.translatable(statusKeyEffective))
        val statusWidth = tr.getWidth(statusText)

        
        val volumeBarWidth = 60

        
        val showRecognizing = recognitionText.isNotEmpty() && now - recognitionTextTimer < textDisplayDurationMs
        val recogWidth = if (showRecognizing) {
            tr.getWidth(Text.translatable("hud.summon", recognitionText))
        } else 0

        
        val showErrorText = errorText.isNotEmpty() && now - errorTextTimer < errorDisplayDurationMs
        val errWidth = if (showErrorText) tr.getWidth(Text.literal("§c$errorText")) else 0

        if (pos == "SPLIT") {
            
            val topX = screenWidth - maxOf(statusWidth, volumeBarWidth) - 10
            context.drawText(tr, statusText, topX, 10, statusColor(recognizing, statusKeyEffective), true)
            drawVolumeBar(context, topX, 26)

            val bottomWidth = maxOf(recogWidth, errWidth)
            val bottomX = screenWidth - bottomWidth - 10
            var by = screenHeight - 10
            if (showErrorText) {
                val alpha = calculateFadeAlpha(now, errorTextTimer, errorDisplayDurationMs)
                drawTextWithAlpha(context, "§c$errorText", bottomX, by - 9, alpha)
                by -= 12
            }
            if (showRecognizing) {
                val alpha = calculateFadeAlpha(now, recognitionTextTimer, textDisplayDurationMs)
                val color = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24 or 0x00FFFFFF
                context.drawText(tr, Text.translatable("hud.summon", recognitionText), bottomX, by - 9, color, true)
            }
            return
        }

        
        val maxRowWidth = maxOf(statusWidth, volumeBarWidth, recogWidth, errWidth)
        val rowHeights = listOf(16, 12) + List(if (showRecognizing) 1 else 0) { 12 } + List(if (showErrorText) 1 else 0) { 12 }
        val totalHeight = rowHeights.sum()

        val (anchorX, anchorY) = resolveAnchor(screenWidth, screenHeight, maxRowWidth, totalHeight, pos)

        var yOffset = anchorY

        
        context.drawText(tr, statusText, anchorX, yOffset, statusColor(recognizing, statusKeyEffective), true)
        yOffset += 16

        
        drawVolumeBar(context, anchorX, yOffset)
        yOffset += 12

        
        if (showRecognizing) {
            val alpha = calculateFadeAlpha(now, recognitionTextTimer, textDisplayDurationMs)
            val color = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24 or 0x00FFFFFF
            context.drawText(
                tr,
                Text.translatable("hud.summon", recognitionText),
                anchorX, yOffset,
                color,
                true
            )
            yOffset += 12
        }

        
        if (showErrorText) {
            val alpha = calculateFadeAlpha(now, errorTextTimer, errorDisplayDurationMs)
            drawTextWithAlpha(context, "§c$errorText", anchorX, yOffset, alpha)
        }
    }

    
    private fun hudPosition(): String = try {
        VoiceSummonClient.config.hudPosition.uppercase().takeIf { it in setOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_RIGHT", "SPLIT") } ?: "TOP_LEFT"
    } catch (e: Exception) {
        "TOP_LEFT"
    }

    
    private fun resolveAnchor(screenWidth: Int, screenHeight: Int, blockWidth: Int, blockHeight: Int, pos: String): Pair<Int, Int> {
        val margin = 10
        val x = when (pos) {
            "TOP_RIGHT", "BOTTOM_RIGHT" -> screenWidth - blockWidth - margin
            else -> margin
        }
        val y = if (pos == "BOTTOM_RIGHT") screenHeight - blockHeight - margin else margin
        return x to y
    }

    
    private fun statusColor(recognizing: Boolean, key: String): Int = when {
        recognizing -> 0xFFFFD700.toInt()  
        key == "hud.status.no_api" || key.contains("disabled") -> 0xFFFF5555.toInt()  
        key.contains("paused") -> 0xFFFFAA00.toInt()  
        key.contains("listening") -> 0xFF55FF55.toInt()  
        else -> 0xFFFFFFFF.toInt()                    
    }

    
    private data class BannerChar(val text: Text, val width: Int, val color: Int)

    private val authorBanner: List<BannerChar> by lazy {
        buildAuthorBanner()
    }

    
    private fun buildAuthorBanner(): List<BannerChar> {
        val text = "VoiceSummon为ThisWuming的原创作品，严禁倒卖。"
        val colors = intArrayOf(0x8B00FF, 0xFF1493, 0x00BFFF, 0x00FF7F)
        val stops = floatArrayOf(0f, 0.33f, 0.66f, 1f)
        val length = text.length
        return buildList(length) {
            for (i in 0 until length) {
                val ratio = if (length <= 1) 0f else i.toFloat() / (length - 1)
                
                val color = (interpolateColor(colors, stops, ratio) and 0x00FFFFFF) or (0xB3 shl 24)
                val ch = text[i].toString()
                add(BannerChar(Text.literal(ch), client.textRenderer.getWidth(ch), color))
            }
        }
    }

    
    private fun drawAuthorBanner(context: DrawContext, now: Long) {
        
        val second = (now / 1000L) % 60L
        if (second >= 3) return

        val tr = client.textRenderer
        
        context.matrices.pushMatrix()
        context.matrices.scale(0.5f, 0.5f)
        var cx = 4
        for (c in authorBanner) {
            context.drawText(tr, c.text, cx, 4, c.color, true)
            cx += c.width
        }
        context.matrices.popMatrix()
    }

    
    private fun interpolateColor(colors: IntArray, stops: FloatArray, ratio: Float): Int {
        
        if (colors.size == 1) return colors[0] or 0xFF000000.toInt()
        if (ratio <= stops[0]) return colors[0] or 0xFF000000.toInt()
        if (ratio >= stops[stops.size - 1]) return colors[colors.size - 1] or 0xFF000000.toInt()

        var idx = 0
        while (idx < stops.size - 1 && ratio > stops[idx + 1]) idx++

        val segStart = stops[idx]
        val segEnd = stops[idx + 1]
        val t = if (segEnd == segStart) 0f else (ratio - segStart) / (segEnd - segStart)

        val c1 = colors[idx]
        val c2 = colors[idx + 1]
        val r = ((c1 shr 16 and 0xFF) + ((c2 shr 16 and 0xFF) - (c1 shr 16 and 0xFF)) * t).toInt()
        val g = ((c1 shr 8 and 0xFF) + ((c2 shr 8 and 0xFF) - (c1 shr 8 and 0xFF)) * t).toInt()
        val b = ((c1 and 0xFF) + ((c2 and 0xFF) - (c1 and 0xFF)) * t).toInt()
        
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    
    private fun drawVolumeBar(context: DrawContext, x: Int, y: Int) {
        val raw = VoiceSummonClient.getCurrentVolume()
        
        val volume = (raw * VOLUME_GAIN).coerceIn(0f, 1f)
        val barWidth = 60
        val barHeight = 4

        
        context.fill(x, y, x + barWidth, y + barHeight, 0x80000000.toInt())

        
        val barColor = when {
            volume < 0.3f -> 0xFF55FF55.toInt()  
            volume < 0.7f -> 0xFFFFFF55.toInt()  
            else -> 0xFFFF5555.toInt()             
        }

        val filledWidth = (barWidth * volume).toInt().coerceIn(0, barWidth)
        if (filledWidth > 0) {
            context.fill(x, y, x + filledWidth, y + barHeight, barColor)
        }
    }

    
    private fun drawTextWithAlpha(context: DrawContext, text: String, x: Int, y: Int, alpha: Float) {
        val color = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24 or 0x00FFFFFF
        context.drawText(
            client.textRenderer,
            Text.literal(text),
            x, y,
            color,
            true
        )
    }

    
    private fun calculateFadeAlpha(now: Long, startTime: Long, duration: Long): Float {
        val elapsed = now - startTime
        if (elapsed > duration) return 0f

        
        val fadeStart = duration * 2 / 3
        return if (elapsed < fadeStart) {
            1.0f
        } else {
            1.0f - ((elapsed - fadeStart).toFloat() / (duration - fadeStart).toFloat())
        }
    }
}