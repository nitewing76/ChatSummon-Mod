package com.thiswuming.voicesummon

import com.thiswuming.voicesummon.config.ModConfig
import com.thiswuming.voicesummon.network.VoiceSummonPacket
import com.thiswuming.voicesummon.summon.SummonManager
import com.thiswuming.voicesummon.util.ModLogger
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object VoiceSummonMod : ModInitializer {

    const val MOD_ID: String = "voicesummon"

    private val logger = ModLogger.getLogger("VoiceSummon")

    lateinit var summonManager: SummonManager
        private set

    override fun onInitialize() {
        logger.info("VoiceSummon mod initializing... (modid={})", MOD_ID)

        
        val serverConfig = ModConfig.load()
        ModLogger.init(serverConfig.debugMode, serverConfig.logToFile)

        
        summonManager = SummonManager()

        
        PayloadTypeRegistry.playC2S().register(VoiceSummonPacket.ID, VoiceSummonPacket.CODEC)

        
        ServerPlayNetworking.registerGlobalReceiver(VoiceSummonPacket.ID) { payload, context ->
            val player = context.player()
            val server = context.server()

            server.execute {
                logger.info("Received summon request from player={}, mobNames={}", player.name.string, payload.mobNames)
                summonManager.handleSummonRequest(server, player, payload.mobNames)
            }
        }

        
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            broadcastJoinNotice(server, handler.player)
        }

        
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            summonManager.onPlayerDisconnect(handler.player)
        }

        logger.info("VoiceSummon mod initialized successfully.")
    }

    
    private fun broadcastJoinNotice(server: MinecraftServer, joiningPlayer: ServerPlayerEntity) {
        val version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        val line1 = gradientText(
            "VoiceSummon为原创作品，严禁倒卖。",
            intArrayOf(0xFF1493, 0xFF0000),
            floatArrayOf(0f, 1f)
        )

        val line2 = Text.empty()
            .append(literal("当前版本：", Formatting.WHITE))
            .append(literal(version, Formatting.WHITE, bold = true))
            .append(literal("（", Formatting.WHITE))
            .append(literal("公开版本", Formatting.RED, bold = true))
            .append(literal("）", Formatting.WHITE))

        val line3 = gradientText(
            "This mod was created by ThisWuming ",
            intArrayOf(0x8B00FF, 0xFF1493, 0x00BFFF, 0x00FF7F),
            floatArrayOf(0f, 0.33f, 0.66f, 1f)
        )

        val line4 = if (joiningPlayer.name.string == "ThisWuming") {
            Text.empty().append(literal("开发者，你好！", Formatting.GOLD, bold = true))
        } else {
            Text.empty()
                .append(literal(joiningPlayer.name.string, Formatting.AQUA, bold = true))
                .append(literal(" ，你好！", Formatting.GREEN))
        }

        joiningPlayer.sendMessage(line1, false)
        joiningPlayer.sendMessage(line2, false)
        joiningPlayer.sendMessage(line3, false)
        joiningPlayer.sendMessage(line4, false)
    }

    
    private fun gradientText(text: String, colors: IntArray, stops: FloatArray): Text {
        val result = Text.empty()
        val length = text.length
        for (i in 0 until length) {
            val ratio = if (length <= 1) 0f else i.toFloat() / (length - 1)
            val color = interpolateColor(colors, stops, ratio)
            result.append(Text.literal(text[i].toString()).styled { it.withColor(color) })
        }
        return result
    }

    
    private fun interpolateColor(colors: IntArray, stops: FloatArray, ratio: Float): Int {
        if (colors.size == 1) return colors[0]
        if (ratio <= stops[0]) return colors[0]
        if (ratio >= stops[stops.size - 1]) return colors[colors.size - 1]

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
        return (r shl 16) or (g shl 8) or b
    }

    private fun literal(text: String, color: Formatting, bold: Boolean = false): Text {
        return Text.literal(text).styled {
            it.withColor(color).apply {
                if (bold) withBold(true)
            }
        }
    }
}