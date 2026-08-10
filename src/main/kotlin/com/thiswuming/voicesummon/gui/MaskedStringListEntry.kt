package com.thiswuming.voicesummon.gui

import me.shedaniel.clothconfig2.gui.entries.StringListEntry
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Supplier


class MaskedStringListEntry(
    fieldName: Text,
    value: String,
    resetButtonKey: Text,
    defaultValue: Supplier<String>,
    saveConsumer: Consumer<String>,
    tooltipSupplier: Supplier<Optional<Array<Text>>>
) : StringListEntry(fieldName, value, resetButtonKey, defaultValue, saveConsumer, tooltipSupplier) {

    init {
        
        textFieldWidget.addFormatter { text, _ ->
            OrderedText.styledForwardsVisitedString("*".repeat(text.length), Style.EMPTY)
        }
    }
}