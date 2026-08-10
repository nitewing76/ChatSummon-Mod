package com.thiswuming.voicesummon.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.thiswuming.voicesummon.VoiceSummonClient
import com.thiswuming.voicesummon.gui.ConfigScreen
import com.thiswuming.voicesummon.util.ModLogger
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.Text


object VoiceCommand {

    private val logger = ModLogger.getLogger("VoiceCommand")

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, registryAccess ->
            registerCommand(dispatcher, registryAccess)
        }
    }

    private fun registerCommand(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        registryAccess: CommandRegistryAccess
    ) {
        dispatcher.register(
            literal<FabricClientCommandSource>("voicesummon")
                .then(
                    literal<FabricClientCommandSource>("on")
                        .executes { ctx ->
                            VoiceSummonClient.setVoiceDetectionEnabled(true)
                            ctx.source.sendFeedback(Text.translatable("command.enabled"))
                            logger.info("Voice detection enabled via command")
                            1
                        }
                )
                .then(
                    literal<FabricClientCommandSource>("off")
                        .executes { ctx ->
                            VoiceSummonClient.setVoiceDetectionEnabled(false)
                            ctx.source.sendFeedback(Text.translatable("command.disabled"))
                            logger.info("Voice detection disabled via command")
                            1
                        }
                )
                .then(
                    literal<FabricClientCommandSource>("status")
                        .executes { ctx ->
                            val status = if (VoiceSummonClient.isVoiceDetectionEnabled()) {
                                Text.translatable("command.status.enabled")
                            } else {
                                Text.translatable("command.status.disabled")
                            }
                            val paused = if (VoiceSummonClient.isPaused()) Text.translatable("command.status.paused") else Text.translatable("command.status.running")
                            ctx.source.sendFeedback(
                                Text.translatable("command.status", status, paused)
                            )
                            1
                        }
                )
                .then(
                    literal<FabricClientCommandSource>("reload")
                        .executes { ctx ->
                            VoiceSummonClient.reloadConfig()
                            ctx.source.sendFeedback(Text.translatable("command.reloaded"))
                            logger.info("Config reloaded via command")
                            1
                        }
                )
                .then(
                    literal<FabricClientCommandSource>("settings")
                        .executes { ctx ->
                            
                            val client = MinecraftClient.getInstance()
                            client.setScreen(ConfigScreen.create(client.currentScreen))
                            ctx.source.sendFeedback(Text.translatable("command.settings_opened"))
                            1
                        }
                )
                .executes { ctx ->
                    ctx.source.sendFeedback(
                        Text.translatable("command.usage")
                    )
                    1
                }
        )
    }
}