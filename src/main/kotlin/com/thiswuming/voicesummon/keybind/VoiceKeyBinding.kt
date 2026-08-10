package com.thiswuming.voicesummon.keybind

import com.thiswuming.voicesummon.VoiceSummonClient
import com.thiswuming.voicesummon.gui.ConfigScreen
import com.thiswuming.voicesummon.hud.VoiceHudOverlay
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW


object VoiceKeyBinding {

    private var toggleKey: KeyBinding? = null
    private var wasPressed: Boolean = false

    private var hudKey: KeyBinding? = null
    private var hudWasPressed: Boolean = false

    private var settingsKey: KeyBinding? = null
    private var settingsWasPressed: Boolean = false

    fun register() {
        
        val category = KeyBinding.Category.create(Identifier.of("voicesummon", "keys"))

        toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.voicesummon.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category
            )
        )

        hudKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.voicesummon.hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
            )
        )

        settingsKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.voicesummon.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                category
            )
        )

        
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            
            val key = toggleKey
            if (key != null) {
                val isPressed = key.isPressed
                if (isPressed && !wasPressed) {
                    VoiceSummonClient.togglePause()
                }
                wasPressed = isPressed
            }

            
            val hud = hudKey
            if (hud != null) {
                val hudPressed = hud.isPressed
                if (hudPressed && !hudWasPressed) {
                    VoiceHudOverlay.toggleHud()
                }
                hudWasPressed = hudPressed
            }

            
            val settings = settingsKey
            if (settings != null) {
                val settingsPressed = settings.isPressed
                if (settingsPressed && !settingsWasPressed) {
                    openSettingsScreen(client)
                }
                settingsWasPressed = settingsPressed
            }
        }
    }

    private fun openSettingsScreen(client: MinecraftClient) {
        try {
            client.setScreen(ConfigScreen.create(client.currentScreen))
        } catch (e: Exception) {
            VoiceSummonClient.logSettingsOpenError(e)
        }
    }
}